package com.tty.api.repository;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tty.api.Log;
import com.tty.api.Scheduler;
import com.tty.api.annotations.entity.CacheKey;
import com.tty.api.dto.PageResult;
import com.tty.api.dto.QueryKey;
import com.tty.api.task.CancellableTask;
import com.tty.api.utils.BaseDataManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public abstract class EntityRepository<T> {

    private final static Log log = Log.create();
    protected final BaseDataManager<T> manager;

    // 实体缓存：键为 QueryKey，值为实体
    private final Map<QueryKey, T> entityCache = new ConcurrentHashMap<>();

    // 分页缓存：键为 PageKey，值为分页结果
    private final Map<PageKey<QueryKey>, PageResult<T>> pageCache = new ConcurrentHashMap<>();

    // 查询条件到分页键的映射（用于分页缓存失效）
    private final Map<QueryKey, Set<PageKey<QueryKey>>> queryToPages = new ConcurrentHashMap<>();

    /**
     * 新增：缓存键字段值 -> 使用了该值的查询的 QueryKey 集合
     * 当实体更新/删除时，根据旧实体的每个缓存键字段值，找到所有关联的查询并清除实体缓存。
     */
    private final Map<Object, Set<QueryKey>> cacheKeyValueToQueryKeys = new ConcurrentHashMap<>();

    private CancellableTask cleanTask;

    // 缓存每个实体类的缓存键字段列表
    private static final Map<Class<?>, List<Field>> CACHE_KEY_FIELDS_CACHE = new ConcurrentHashMap<>();

    public EntityRepository(BaseDataManager<T> manager) {
        this.manager = manager;
        this.debug("EntityRepository initialized with manager: {}", manager.getClass().getSimpleName());
    }

    /**
     * 从实体中提取主键查询条件（用于主键缓存）
     */
    protected abstract @NotNull LambdaQueryWrapper<T> extractCacheKey(T entity);

    /**
     * 从实体中提取分页查询条件（用于分页缓存失效）
     * 当实体变更时，所有符合此条件的分页缓存将被清除。
     */
    protected abstract LambdaQueryWrapper<T> extractPageQueryKey(T entity);

    protected QueryKey wrapQueryKey(LambdaQueryWrapper<T> wrapper) {
        return QueryKey.of(wrapper);
    }

    private List<Field> getCacheKeyFields(Class<?> entityClass) {
        return CACHE_KEY_FIELDS_CACHE.computeIfAbsent(entityClass, clazz -> {
            List<Field> fields = new ArrayList<>();

            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(CacheKey.class)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }

            TableInfoHelper.getTableInfo(clazz);
            var tableInfo = TableInfoHelper.getTableInfo(clazz);
            if (tableInfo != null && tableInfo.getKeyProperty() != null) {
                try {
                    Field pkField = clazz.getDeclaredField(tableInfo.getKeyProperty());
                    pkField.setAccessible(true);
                    // 避免重复添加
                    if (fields.stream().noneMatch(f -> f.getName().equals(pkField.getName()))) {
                        fields.add(pkField);
                    }
                } catch (NoSuchFieldException ignored) {}
            }
            return fields;
        });
    }

    private Map<Object, String> extractCacheKeyValues(T entity) {
        Map<Object, String> values = new HashMap<>();
        List<Field> fields = getCacheKeyFields(entity.getClass());
        for (Field field : fields) {
            try {
                Object value = field.get(entity);
                if (value != null) {
                    values.put(value, field.getName());
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return values;
    }

    protected final void debug(String format, Object... args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = getClass().getSimpleName();
        System.arraycopy(args, 0, merged, 1, args.length);
        log.debug("[{}] " + format, merged);
    }

    public CompletableFuture<T> get(LambdaQueryWrapper<T> key) {
        QueryKey queryKey = this.wrapQueryKey(key);
        T cached = this.entityCache.get(queryKey);
        if (cached != null) {
            this.debug("Entity cache hit for key: {}", queryKey);
            return CompletableFuture.completedFuture(cached);
        }

        this.debug("Entity cache miss for key: {}, querying from DB", queryKey);

        if (this.manager == null) {
            this.debug("Manager is null, returning null for key: {}", queryKey);
            return CompletableFuture.completedFuture(null);
        }

        return this.manager.getInstance(key).thenApply(entity -> {
            if (entity != null) {

                this.entityCache.put(queryKey, entity);
                this.debug("Entity cached for key: {}", queryKey);

                LambdaQueryWrapper<T> pkWrapper = this.extractCacheKey(entity);
                QueryKey pkQueryKey = this.wrapQueryKey(pkWrapper);
                boolean isPrimaryKeyQuery = queryKey.equals(pkQueryKey);

                if (!isPrimaryKeyQuery) {
                    Map<Object, String> cacheValues = extractCacheKeyValues(entity);
                    for (Object value : cacheValues.keySet()) {
                        this.cacheKeyValueToQueryKeys
                                .computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
                                .add(queryKey);
                        this.debug("Recorded query {} for cache key value: {} ({})",
                                queryKey, value, cacheValues.get(value));
                    }
                } else {
                    this.debug("Primary key query, no need to record mapping");
                }
            } else {
                this.debug("Entity not found for key: {}", queryKey);
            }
            return entity;
        });
    }

    public CompletableFuture<T> create(T entity) {
        this.debug("Creating new entity: {}", entity);
        return this.manager.createInstance(entity).thenApply(createdEntity -> {
            if (createdEntity != null) {
                LambdaQueryWrapper<T> cacheKeyWrapper = this.extractCacheKey(createdEntity);
                QueryKey cacheKey = this.wrapQueryKey(cacheKeyWrapper);
                this.entityCache.put(cacheKey, createdEntity);
                this.debug("New entity cached with key: {}", cacheKey);
                this.handleCreateForAscendingOrder(createdEntity);
                this.debug("Entity created and cached successfully: {}", cacheKey);
            } else {
                this.debug("Entity creation failed for: {}", entity);
            }
            return createdEntity;
        });
    }

    public CompletableFuture<Boolean> update(T entity) {

        LambdaQueryWrapper<T> pkWrapper = this.extractCacheKey(entity);
        QueryKey pkQueryKey = this.wrapQueryKey(pkWrapper);

        T oldEntity = this.entityCache.get(pkQueryKey);
        if (oldEntity == null) {
            this.debug("Old entity not found in cache, loading from DB for key: {}", pkQueryKey);
            oldEntity = this.manager.getInstance(pkWrapper).join();
        }

        T finalOldEntity = oldEntity;
        return this.manager.modify(entity).thenApply(success -> {
            if (success) {
                this.entityCache.put(pkQueryKey, entity);
                this.debug("Entity updated in cache: {}", pkQueryKey);

                if (finalOldEntity != null) {
                    Map<Object, String> oldCacheValues = extractCacheKeyValues(finalOldEntity);
                    for (Object oldValue : oldCacheValues.keySet()) {
                        Set<QueryKey> relatedQueries = this.cacheKeyValueToQueryKeys.remove(oldValue);
                        if (relatedQueries != null) {
                            for (QueryKey qk : relatedQueries) {
                                this.entityCache.remove(qk);
                                this.debug("Removed entity cache for query {} (due to cache key value: {} - {})", qk, oldValue, oldCacheValues.get(oldValue));
                            }
                        }
                    }
                }
                this.invalidateRelatedPages(entity);
            } else {
                this.debug("Entity update failed for key: {}", pkQueryKey);
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> delete(T entity) {
        LambdaQueryWrapper<T> pkWrapper = this.extractCacheKey(entity);
        QueryKey pkQueryKey = this.wrapQueryKey(pkWrapper);

        T cachedEntity = this.entityCache.get(pkQueryKey);
        CompletableFuture<T> entityFuture;
        if (cachedEntity != null) {
            entityFuture = CompletableFuture.completedFuture(cachedEntity);
        } else {
            entityFuture = this.manager.getInstance(pkWrapper);
        }

        return entityFuture.thenCompose(e -> {
            if (e == null) {
                this.debug("Entity not found for deletion, key: {}", pkQueryKey);
                return CompletableFuture.completedFuture(false);
            }

            return this.manager.deleteInstance(e).thenApply(success -> {
                if (success) {
                    // 移除主键缓存
                    this.entityCache.remove(pkQueryKey);
                    this.debug("Entity removed from cache: {}", pkQueryKey);

                    Map<Object, String> cacheValues = extractCacheKeyValues(e);
                    for (Object value : cacheValues.keySet()) {
                        Set<QueryKey> relatedQueries = this.cacheKeyValueToQueryKeys.remove(value);
                        if (relatedQueries != null) {
                            for (QueryKey qk : relatedQueries) {
                                this.entityCache.remove(qk);
                                this.debug("Removed entity cache for query {} (due to cache key value: {} - {})", qk, value, cacheValues.get(value));
                            }
                        }
                    }

                    // 失效分页缓存
                    this.invalidateRelatedPages(e);
                } else {
                    this.debug("Entity deletion failed for key: {}", pkQueryKey);
                }
                return success;
            });
        });
    }

    public CompletableFuture<PageResult<T>> getList(int pageNum, int pageSize, LambdaQueryWrapper<T> queryCondition) {
        QueryKey queryKey = this.wrapQueryKey(queryCondition);
        PageKey<QueryKey> pageKey = new PageKey<>(pageNum, pageSize, queryKey);

        PageResult<T> cached = this.pageCache.get(pageKey);
        if (cached != null) {
            this.debug("Page cache hit for pageKey: {}", pageKey);
            return CompletableFuture.completedFuture(cached);
        }

        this.debug("Page cache miss for pageKey: {}, querying from DB", pageKey);

        if (this.manager == null) {
            this.debug("Manager is null, returning empty page result");
            return CompletableFuture.completedFuture(PageResult.build(Collections.emptyList(), 0, 0, pageNum));
        }

        return this.manager.getList(pageNum, pageSize, queryCondition).thenApply(result -> {
            if (result == null) {
                this.debug("Query returned null result for pageKey: {}", pageKey);
                return null;
            }

            this.pageCache.put(pageKey, result);
            this.debug("Page cached for pageKey: {}, record count: {}", pageKey, result.records().size());

            this.queryToPages.computeIfAbsent(queryKey, k -> ConcurrentHashMap.newKeySet()).add(pageKey);
            this.debug("Added pageKey mapping for query condition: {} -> {}", queryKey, pageKey);

            int entityCachedCount = 0;
            for (T entity : result.records()) {
                LambdaQueryWrapper<T> entityKeyWrapper = this.extractCacheKey(entity);
                QueryKey entityKey = this.wrapQueryKey(entityKeyWrapper);
                this.entityCache.put(entityKey, entity);
                entityCachedCount++;
            }

            if (entityCachedCount > 0) {
                this.debug("Cached {} entities from page result", entityCachedCount);
            }

            return result;
        });
    }

    private void invalidateRelatedPages(T entity) {
        if (entity == null) {
            this.debug("Cannot invalidate pages for null entity");
            return;
        }

        LambdaQueryWrapper<T> pageQueryKeyWrapper = this.extractPageQueryKey(entity);
        if (pageQueryKeyWrapper == null) {
            this.debug("Entity type does not require page cache invalidation");
            return;
        }

        QueryKey queryKey = this.wrapQueryKey(pageQueryKeyWrapper);
        this.debug("Invalidating pages related to entity with query key: {}", queryKey);
        this.invalidatePagesByQueryKey(queryKey);
    }

    private void invalidatePagesByQueryKey(QueryKey queryKey) {
        Set<PageKey<QueryKey>> relatedPages = this.queryToPages.remove(queryKey);
        if (relatedPages == null || relatedPages.isEmpty()) {
            this.debug("No related pages found for query key: {}", queryKey);
            return;
        }

        int invalidatedCount = 0;
        for (PageKey<QueryKey> pageKey : relatedPages) {
            this.pageCache.remove(pageKey);
            invalidatedCount++;
        }

        this.debug("Invalidated {} pages for query key: {}", invalidatedCount, queryKey);
    }

    private void handleCreateForAscendingOrder(T newEntity) {
        LambdaQueryWrapper<T> pageQueryKeyWrapper = this.extractPageQueryKey(newEntity);
        if (pageQueryKeyWrapper == null) return;

        QueryKey queryKey = this.wrapQueryKey(pageQueryKeyWrapper);
        Set<PageKey<QueryKey>> relatedPages = this.queryToPages.get(queryKey);

        if (relatedPages == null || relatedPages.isEmpty()) {
            this.debug("No cached pages found for new entity, query key: {}", queryKey);
            return;
        }

        int maxPageNum = 0;
        for (PageKey<QueryKey> pageKey : relatedPages) {
            if (pageKey.pageNum() > maxPageNum) {
                maxPageNum = pageKey.pageNum();
            }
        }

        PageKey<QueryKey> lastPageKey = null;
        for (PageKey<QueryKey> pageKey : relatedPages) {
            if (pageKey.pageNum() == maxPageNum) {
                lastPageKey = pageKey;
                break;
            }
        }

        if (lastPageKey != null) {
            PageResult<T> lastPage = this.pageCache.get(lastPageKey);
            if (lastPage != null) {
                int pageSize = lastPageKey.pageSize();
                int recordCount = lastPage.records().size();

                if (recordCount < pageSize) {
                    this.pageCache.remove(lastPageKey);
                    relatedPages.remove(lastPageKey);
                    this.debug("Last page not full ({}/{}), invalidated only last page: {}",
                            recordCount, pageSize, lastPageKey);
                } else {
                    this.invalidatePagesByQueryKey(queryKey);
                    this.debug("Last page is full ({}/{}), invalidated all pages for query: {}",
                            recordCount, pageSize, queryKey);
                }
            } else {
                this.debug("Last page cache entry not found for pageKey: {}", lastPageKey);
            }
        } else {
            this.debug("No last page key found for query: {}", queryKey);
        }
    }

    public void setExecutionMode(boolean value) {
        this.manager.setExecutionMode(value);
    }

    public boolean isAsync() {
        return this.manager.isAsync;
    }

    public void clearEntityCache() {
        int size = this.entityCache.size();
        this.entityCache.clear();
        this.cacheKeyValueToQueryKeys.clear(); // 清理映射关系
        this.debug("Cleared entity cache, removed {} entities", String.valueOf(size));
    }

    public void clearPageCache() {
        int pageCount = this.pageCache.size();
        this.pageCache.clear();
        this.queryToPages.clear();
        this.debug("Cleared page cache, removed {} pages", String.valueOf(pageCount));
    }

    public void clearAllCache() {
        int entityCount = this.entityCache.size();
        int pageCount = this.pageCache.size();

        this.clearEntityCache();
        this.clearPageCache();

        this.debug("Cleared all cache, removed {} entities and {} pages", String.valueOf(entityCount), String.valueOf(pageCount));
    }

    public void autoClean(Scheduler scheduler, JavaPlugin plugin, long delay, long period) {
        if (this.cleanTask != null) return;
        this.cleanTask = scheduler.runAsyncAtFixedRate(plugin, i -> {
            try {
                this.clearAllCache();
                this.debug("Scheduled cache clear executed.");
            } catch (Exception e) {
                log.error(e, "Error during scheduled cache clear");
            }
        }, delay, period);
    }

    public void stopAutoClean() {
        if (this.cleanTask == null) return;
        this.cleanTask.cancel();
        this.cleanTask = null;
        this.debug("Auto clear stopped.");
    }

    public void debug(boolean status) {
        log.setDebug(status);
    }
}