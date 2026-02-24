package com.tty.api.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tty.api.Log;
import com.tty.api.dto.PageResult;
import com.tty.api.dto.QueryKey;
import com.tty.api.utils.BaseDataManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 实体仓库抽象基类，为实体提供带有两级缓存（实体缓存 + 分页缓存）的异步 CRUD 操作。
 * @param <T> 实体类型
 */
public abstract class EntityRepository<T> {

    private static final Log log = Log.create();

    protected final BaseDataManager<T> manager;

    // 实体缓存，键为分区+查询条件，值为单个实体
    private final Cache<@NotNull PartitionedKey<QueryKey>, T> entityCache =
            Caffeine.newBuilder()
                    .maximumSize(2000)
                    .expireAfterWrite(300, TimeUnit.MINUTES)
                    .build();

    // 分页缓存，键为分区+分页条件，值为分页结果
    private final Cache<@NotNull PartitionedKey<PageKey<QueryKey>>, PageResult<T>> pageCache =
            Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(300, TimeUnit.MINUTES)
                    .build();

    // 正在进行的实体加载任务，用于防止缓存击穿
    private final ConcurrentHashMap<PartitionedKey<QueryKey>, CompletableFuture<T>> pendingEntityFutures = new ConcurrentHashMap<>();
    // 正在进行的分页加载任务，用于防止缓存击穿
    private final ConcurrentHashMap<PartitionedKey<PageKey<QueryKey>>, CompletableFuture<PageResult<T>>> pendingPageFutures = new ConcurrentHashMap<>();

    // 缓存实体类的主键 Field，避免重复反射
    private static final Map<Class<?>, Field> PK_FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 构造方法，注入数据管理器
     * @param manager 实际执行数据库操作的管理器
     */
    public EntityRepository(BaseDataManager<T> manager) {
        this.manager = manager;
        this.debug("EntityRepository initialized with manager: {}", manager != null ? manager.getClass().getSimpleName() : "null");
    }

    /**
     * 将 MyBatis-Plus 查询包装器转换为缓存的查询键
     * @param wrapper 查询条件
     * @return 查询键
     */
    protected QueryKey wrapQueryKey(LambdaQueryWrapper<T> wrapper) {
        return QueryKey.of(wrapper);
    }

    /**
     * 提取实体的主键值（反射实现，结果缓存在 PK_FIELD_CACHE 中）
     * @param entity 实体对象
     * @return 主键值，若无法提取则返回 null
     */
    private Object extractPrimaryKeyValue(T entity) {
        if (entity == null) return null;

        Field pkField = PK_FIELD_CACHE.get(entity.getClass());
        if (pkField == null) {
            TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
            if (tableInfo == null || tableInfo.getKeyProperty() == null) {
                this.debug("No primary key property found for class: {}", entity.getClass().getSimpleName());
                return null;
            }

            try {
                pkField = entity.getClass().getDeclaredField(tableInfo.getKeyProperty());
                pkField.setAccessible(true);
                PK_FIELD_CACHE.put(entity.getClass(), pkField);
            } catch (NoSuchFieldException e) {
                this.debug("Primary key field '{}' not found in class: {}", tableInfo.getKeyProperty(), entity.getClass().getSimpleName());
                return null;
            }
        }

        try {
            return pkField.get(entity);
        } catch (IllegalAccessException e) {
            this.debug("Failed to access primary key field: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据实体和分区构建基于主键的查询键（用于实体缓存）
     * @param entity 实体对象
     * @param partition 分区键
     * @return 分区+主键查询键，若无法构建则返回 null
     */
    private PartitionedKey<QueryKey> buildPrimaryKeyQueryKey(T entity, PartitionKey partition) {
        Object id = extractPrimaryKeyValue(entity);
        if (id == null) {
            this.debug("Cannot build primary key query key, id is null for entity: {}", entity);
            return null;
        }

        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        if (tableInfo == null || tableInfo.getKeyColumn() == null) {
            this.debug("No key column info for class: {}", entity.getClass().getSimpleName());
            return null;
        }

        LambdaQueryWrapper<T> pkWrapper = new LambdaQueryWrapper<>();
        pkWrapper.apply(tableInfo.getKeyColumn() + " = {0}", id);
        return new PartitionedKey<>(partition, wrapQueryKey(pkWrapper));
    }

    /**
     * 统一的调试日志输出，自动添加类名前缀
     * @param format 格式字符串
     * @param args 参数
     */
    protected final void debug(String format, Object... args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = getClass().getSimpleName();
        System.arraycopy(args, 0, merged, 1, args.length);
        log.debug("[{}] " + format, merged);
    }

    /**
     * 根据查询条件获取单个实体（异步，带缓存）
     * @param key 查询条件
     * @param partition 分区键
     * @return 实体 CompletableFuture，可能为 null
     */
    public CompletableFuture<T> get(LambdaQueryWrapper<T> key, PartitionKey partition) {
        QueryKey queryKey = wrapQueryKey(key);
        PartitionedKey<QueryKey> pKey = new PartitionedKey<>(partition, queryKey);

        T cached = this.entityCache.getIfPresent(pKey);
        if (cached != null) {
            this.debug("Entity cache hit: {}", pKey);
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<T> pending = pendingEntityFutures.get(pKey);
        if (pending != null) {
            this.debug("Entity pending future found: {}", pKey);
            return pending;
        }

        CompletableFuture<T> newFuture = new CompletableFuture<>();
        CompletableFuture<T> existing = this.pendingEntityFutures.putIfAbsent(pKey, newFuture);
        if (existing != null) {
            this.debug("Entity pending future (race) found: {}", pKey);
            return existing;
        }

        this.debug("Entity cache miss: {}, loading from DB", pKey);
        if (this.manager == null) {
            newFuture.complete(null);
            this.pendingEntityFutures.remove(pKey, newFuture);
            return newFuture;
        }

        this.manager.get(key).whenComplete((entity, throwable) -> {
            if (throwable != null) {
                this.debug("Error loading entity for key {}: {}", pKey, throwable.getMessage());
                newFuture.completeExceptionally(throwable);
            } else {
                if (entity != null) {
                    this.entityCache.put(pKey, entity);
                    this.debug("Entity cached: {}", pKey);
                }
                newFuture.complete(entity);
            }
            this.pendingEntityFutures.remove(pKey, newFuture);
        });

        return newFuture;
    }

    /**
     * 分页查询实体列表（异步，带缓存）
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @param condition 查询条件
     * @param partition 分区键
     * @return 分页结果 CompletableFuture
     */
    public CompletableFuture<PageResult<T>> getList(int pageNum, int pageSize,
                                                    LambdaQueryWrapper<T> condition,
                                                    PartitionKey partition) {
        QueryKey queryKey = wrapQueryKey(condition);
        PageKey<QueryKey> pageKey = new PageKey<>(pageNum, pageSize, queryKey);
        PartitionedKey<PageKey<QueryKey>> pPageKey = new PartitionedKey<>(partition, pageKey);

        PageResult<T> cached = this.pageCache.getIfPresent(pPageKey);
        if (cached != null) {
            this.debug("Page cache hit: {}", pPageKey);
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<PageResult<T>> pending = this.pendingPageFutures.get(pPageKey);
        if (pending != null) {
            this.debug("Page pending future found: {}", pPageKey);
            return pending;
        }

        CompletableFuture<PageResult<T>> newFuture = new CompletableFuture<>();
        CompletableFuture<PageResult<T>> existing = this.pendingPageFutures.putIfAbsent(pPageKey, newFuture);
        if (existing != null) {
            return existing;
        }

        this.debug("Page cache miss: {}, querying DB", pPageKey);
        if (this.manager == null) {
            newFuture.complete(PageResult.build(Collections.emptyList(), 0, 0, pageNum));
            this.pendingPageFutures.remove(pPageKey, newFuture);
            return newFuture;
        }

        this.manager.getList(pageNum, pageSize, condition).whenComplete((result, throwable) -> {
            if (throwable != null) {
                this.debug("Error loading page for key {}: {}", pPageKey, throwable.getMessage());
                newFuture.completeExceptionally(throwable);
            } else {
                if (result != null) {
                    this.pageCache.put(pPageKey, result);
                    // 同时缓存结果中的每个实体，便于后续按主键查询命中
                    for (T entity : result.records()) {
                        PartitionedKey<QueryKey> entityKey = buildPrimaryKeyQueryKey(entity, partition);
                        if (entityKey == null) {
                            LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>(entity);
                            entityKey = new PartitionedKey<>(partition, wrapQueryKey(wrapper));
                        }
                        this.entityCache.put(entityKey, entity);
                        this.debug("Entity cached from page result: {}", entityKey);
                    }
                }
                newFuture.complete(result);
            }
            this.pendingPageFutures.remove(pPageKey, newFuture);
        });

        return newFuture;
    }

    /**
     * 创建实体（异步），成功后会缓存实体并使对应分区的所有分页缓存失效
     * @param entity 待创建的实体
     * @param partition 分区键
     * @return 创建后的实体 CompletableFuture
     */
    public CompletableFuture<T> create(T entity, PartitionKey partition) {
        if (this.manager == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.manager.create(entity).thenApply(created -> {
            if (created != null) {
                // 缓存新实体
                PartitionedKey<QueryKey> pkKey = buildPrimaryKeyQueryKey(created, partition);
                if (pkKey != null) {
                    this.entityCache.put(pkKey, created);
                } else {
                    LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>(created);
                    pkKey = new PartitionedKey<>(partition, wrapQueryKey(wrapper));
                    this.entityCache.put(pkKey, created);
                }
                this.debug("Entity created successfully: {}", created);
                this.debug("Entity cached after create: {}", pkKey);
                // 清空该分区所有页缓存（因为新增可能影响分页结果）
                this.invalidateAllPagesInPartition(partition);
            }
            return created;
        });
    }

    /**
     * 更新实体（异步），成功后会更新缓存并使对应分区的所有分页缓存失效
     * @param entity 更新后的实体（应包含主键）
     * @param key 更新条件（通常应能唯一定位到该实体）
     * @param partition 分区键
     * @return 是否更新成功
     */
    public CompletableFuture<Boolean> update(T entity, LambdaQueryWrapper<T> key, PartitionKey partition) {
        if (this.manager == null) {
            return CompletableFuture.completedFuture(false);
        }
        return this.manager.update(entity, key).thenApply(success -> {
            if (success) {
                PartitionedKey<QueryKey> pkKey = buildPrimaryKeyQueryKey(entity, partition);
                if (pkKey == null) {
                    LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>(entity);
                    pkKey = new PartitionedKey<>(partition, wrapQueryKey(wrapper));
                }
                this.entityCache.put(pkKey, entity);
                this.pendingEntityFutures.remove(pkKey);
                this.debug("Entity updated successfully: {}", entity);
                this.debug("Entity cached after update: {}", pkKey);
                this.invalidateAllPagesInPartition(partition);
            }
            return success;
        });
    }

    /**
     * 删除实体（异步），成功后会清除缓存并使对应分区的所有分页缓存失效
     * @param key 删除条件（通常应能唯一定位到该实体）
     * @param partition 分区键
     * @return 是否删除成功
     */
    public CompletableFuture<Boolean> delete(LambdaQueryWrapper<T> key, PartitionKey partition) {
        if (this.manager == null) {
            return CompletableFuture.completedFuture(false);
        }
        return this.manager.get(key).thenCompose(entity -> {
            if (entity == null) {
                this.debug("Entity not found for deletion with key: {}, partition: {}", key, partition);
                return CompletableFuture.completedFuture(false);
            }
            return this.manager.delete(key).thenApply(success -> {
                if (!success) return false;
                PartitionedKey<QueryKey> pkKey = buildPrimaryKeyQueryKey(entity, partition);
                if (pkKey == null) {
                    LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>(entity);
                    pkKey = new PartitionedKey<>(partition, wrapQueryKey(wrapper));
                }
                this.entityCache.invalidate(pkKey);
                this.pendingEntityFutures.remove(pkKey); // 移除可能正在进行的加载任务
                this.debug("Entity deleted successfully: {}", entity);
                this.invalidateAllPagesInPartition(partition);
                return true;
            });
        });
    }

    /**
     * 使指定分区的所有分页缓存失效（同时清理对应的 pending future）
     * @param partition 分区键
     */
    private void invalidateAllPagesInPartition(PartitionKey partition) {
        this.pageCache.asMap().keySet().removeIf(pPageKey -> pPageKey.partition().equals(partition));
        this.pendingPageFutures.keySet().removeIf(pPageKey -> pPageKey.partition().equals(partition));
        this.debug("Invalidated all page caches for partition: {}", partition);
    }

    /**
     * 清空实体缓存
     */
    public void clearEntityCache() {
        this.entityCache.invalidateAll();
        this.debug("Entity cache cleared");
    }

    /**
     * 清空分页缓存
     */
    public void clearPageCache() {
        this.pageCache.invalidateAll();
        this.debug("Page cache cleared");
    }

    /**
     * 清空所有缓存（实体+分页）
     */
    public void clearAllCache() {
        this.clearEntityCache();
        this.clearPageCache();
        this.debug("All caches cleared");
    }

    /**
     * 设置执行模式（同步/异步），委托给 manager
     * @param async true 异步，false 同步
     */
    public void setExecutionMode(boolean async) {
        this.manager.setExecutionMode(async);
    }

    /**
     * 当前是否为异步模式
     * @return true 异步，false 同步
     */
    public boolean isAsync() {
        return this.manager.isAsync();
    }

    /**
     * 开启/关闭调试日志
     * @param status true 开启，false 关闭
     */
    public void debug(boolean status) {
        log.setDebug(status);
    }

    /**
     * 中止所有正在进行的加载任务，应在服务器关闭时调用以释放资源。
     */
    public void abort() {
        this.debug("Aborting all pending futures...");
        this.pendingEntityFutures.values().forEach(f -> f.cancel(true));
        this.pendingPageFutures.values().forEach(f -> f.cancel(true));
        this.pendingEntityFutures.clear();
        this.pendingPageFutures.clear();
        this.debug("All pending futures aborted.");
    }

}