package com.tty.api.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tty.api.Log;
import com.tty.api.annotations.entity.CacheKey;
import com.tty.api.dto.PageResult;
import com.tty.api.dto.QueryKey;
import com.tty.api.utils.BaseDataManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public abstract class EntityRepository<T> {

    private final static Log log = Log.create();
    protected final BaseDataManager<T> manager;

    private final Cache<@NotNull PartitionedKey<QueryKey>, T> entityCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final Cache<@NotNull PartitionedKey<PageKey<QueryKey>>, PageResult<T>> pageCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final Map<PartitionedKey<QueryKey>, Set<PartitionedKey<PageKey<QueryKey>>>> queryToPages = new ConcurrentHashMap<>();

    private final Map<PartitionKey, Map<String, Map<Object, Set<PartitionedKey<QueryKey>>>>> fieldReverseIndex = new ConcurrentHashMap<>();

    private final Map<PartitionKey, Map<Object, Set<PartitionedKey<PageKey<QueryKey>>>>> entityIdToPages = new ConcurrentHashMap<>();

    private final Map<PartitionedKey<PageKey<QueryKey>>, Set<Object>> pageToEntityIds = new ConcurrentHashMap<>();

    private static final Map<Class<?>, List<Field>> CACHE_KEY_FIELDS_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Field> PK_FIELD_CACHE = new ConcurrentHashMap<>();

    public EntityRepository(BaseDataManager<T> manager) {
        this.manager = manager;
        this.debug("EntityRepository initialized with manager: {}", manager != null ? manager.getClass().getSimpleName() : "null");
    }

    protected abstract @Nullable Object resolvePartitionKey(T entity);

    protected abstract @Nullable Object resolvePartitionKey(LambdaQueryWrapper<T> wrapper);

    private PartitionKey partitionOf(T entity) {
        return PartitionKey.of(resolvePartitionKey(entity));
    }

    private PartitionKey partitionOf(LambdaQueryWrapper<T> wrapper) {
        return PartitionKey.of(resolvePartitionKey(wrapper));
    }

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
            var tableInfo = TableInfoHelper.getTableInfo(clazz);
            if (tableInfo != null && tableInfo.getKeyProperty() != null) {
                try {
                    Field pkField = clazz.getDeclaredField(tableInfo.getKeyProperty());
                    pkField.setAccessible(true);
                    if (fields.stream().noneMatch(f -> f.getName().equals(pkField.getName()))) {
                        fields.add(pkField);
                    }
                    PK_FIELD_CACHE.putIfAbsent(clazz, pkField);
                } catch (NoSuchFieldException ignored) {}
            }
            return fields;
        });
    }

    private Map<String, Object> extractCacheKeyValuesByFieldName(T entity) {
        Map<String, Object> values = new HashMap<>();
        if (entity == null) return values;
        List<Field> fields = this.getCacheKeyFields(entity.getClass());
        for (Field field : fields) {
            try {
                Object value = field.get(entity);
                values.put(field.getName(), value);
            } catch (IllegalAccessException ignored) {}
        }
        return values;
    }

    private Object extractPrimaryKeyValue(T entity) {
        if (entity == null) return null;
        Field pkField = PK_FIELD_CACHE.get(entity.getClass());
        if (pkField == null) {
            var tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
            if (tableInfo == null || tableInfo.getKeyProperty() == null) return null;
            try {
                Field f = entity.getClass().getDeclaredField(tableInfo.getKeyProperty());
                f.setAccessible(true);
                PK_FIELD_CACHE.put(entity.getClass(), f);
                pkField = f;
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }
        try {
            return pkField.get(entity);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    protected final void debug(String format, Object... args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = getClass().getSimpleName();
        System.arraycopy(args, 0, merged, 1, args.length);
        log.debug("[{}] " + format, merged);
    }

    protected Set<String> extractFieldsUsedInQuery(LambdaQueryWrapper<T> wrapper) {
        if (wrapper.getEntityClass() != null) {
            return getCacheKeyFields(wrapper.getEntityClass()).stream().map(Field::getName).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    protected boolean isPrimaryKeyQuery(LambdaQueryWrapper<T> wrapper) {
        if (wrapper == null) return false;
        T sample = wrapper.getEntity();
        if (sample == null) return false;
        var tableInfo = TableInfoHelper.getTableInfo(sample.getClass());
        if (tableInfo == null || tableInfo.getKeyProperty() == null) return false;
        try {
            Field pkField = sample.getClass().getDeclaredField(tableInfo.getKeyProperty());
            pkField.setAccessible(true);
            Object pkVal = pkField.get(sample);
            return pkVal != null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }

    private void recordQueryForCacheKeyValues(PartitionKey partition, PartitionedKey<QueryKey> pQueryKey, Map<String, Object> fieldToValue, Set<String> fieldsUsedInQuery) {
        if (fieldsUsedInQuery == null || fieldsUsedInQuery.isEmpty()) return;
        Map<String, Map<Object, Set<PartitionedKey<QueryKey>>>> partitionIndex =
                fieldReverseIndex.computeIfAbsent(partition, k -> new ConcurrentHashMap<>());

        for (Map.Entry<String, Object> e : fieldToValue.entrySet()) {
            String fieldName = e.getKey();
            Object value = e.getValue();
            if (value == null) continue;
            if (!fieldsUsedInQuery.contains(fieldName)) continue;
            partitionIndex
                    .computeIfAbsent(fieldName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
                    .add(pQueryKey);
            this.debug("Recorded mapping partition={} field={} value={} -> query {}", partition, fieldName, value, pQueryKey);
        }
    }

    private void recordPageEntities(PartitionKey partition, PartitionedKey<PageKey<QueryKey>> pPageKey, List<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        Set<Object> ids = ConcurrentHashMap.newKeySet();
        for (T e : entities) {
            Object id = extractPrimaryKeyValue(e);
            if (id == null) continue;
            ids.add(id);
            entityIdToPages
                    .computeIfAbsent(partition, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
                    .add(pPageKey);
        }
        pageToEntityIds.put(pPageKey, ids);
    }

    private void removePageFromEntityIndex(PartitionKey partition, PartitionedKey<PageKey<QueryKey>> pPageKey) {
        Set<Object> ids = pageToEntityIds.remove(pPageKey);
        if (ids == null || ids.isEmpty()) return;
        Map<Object, Set<PartitionedKey<PageKey<QueryKey>>>> partitionEntityMap = entityIdToPages.get(partition);
        if (partitionEntityMap == null) return;
        for (Object id : ids) {
            Set<PartitionedKey<PageKey<QueryKey>>> pages = partitionEntityMap.get(id);
            if (pages != null) {
                pages.remove(pPageKey);
                if (pages.isEmpty()) {
                    partitionEntityMap.remove(id);
                }
            }
        }
    }

    public CompletableFuture<T> get(LambdaQueryWrapper<T> key) {
        PartitionKey partition = partitionOf(key);
        QueryKey queryKey = this.wrapQueryKey(key);
        PartitionedKey<QueryKey> pQueryKey = new PartitionedKey<>(partition, queryKey);

        T cached = this.entityCache.getIfPresent(pQueryKey);
        if (cached != null) {
            this.debug("Entity cache hit for key: {}", pQueryKey);
            return CompletableFuture.completedFuture(cached);
        }

        this.debug("Entity cache miss for key: {}, querying from DB", pQueryKey);

        if (this.manager == null) {
            this.debug("Manager is null, returning null for key: {}", pQueryKey);
            return CompletableFuture.completedFuture(null);
        }

        return this.manager.get(key).thenApply(entity -> {
            if (entity != null) {
                this.entityCache.put(pQueryKey, entity);
                this.debug("Entity cached for key: {}", pQueryKey);

                boolean primary = this.isPrimaryKeyQuery(key);
                if (!primary) {
                    Set<String> fieldsUsedInQuery = this.extractFieldsUsedInQuery(key);
                    Map<String, Object> cacheValues = this.extractCacheKeyValuesByFieldName(entity);
                    recordQueryForCacheKeyValues(partition, pQueryKey, cacheValues, fieldsUsedInQuery);
                } else {
                    this.debug("Primary key query detected, skip recording field->query mapping for {}", pQueryKey);
                }
            } else {
                this.debug("Entity not found for key: {}", pQueryKey);
            }
            return entity;
        });
    }

    public CompletableFuture<T> create(T entity) {
        this.debug("Creating new entity: {}", entity);
        if (this.manager == null) {
            this.debug("Manager is null, cannot create");
            return CompletableFuture.completedFuture(null);
        }

        return this.manager.create(entity).thenApply(createdEntity -> {
            if (createdEntity != null) {
                PartitionKey partition = partitionOf(createdEntity);
                LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>(createdEntity);
                PartitionedKey<QueryKey> pk = new PartitionedKey<>(partition, wrapQueryKey(wrapper));
                this.entityCache.put(pk, createdEntity);
                this.debug("New entity cached with key: {}", pk);
                Map<String, Object> cacheValues = extractCacheKeyValuesByFieldName(createdEntity);
                Set<String> fieldsUsedInQuery = Collections.emptySet();
                triggerInvalidationByFields(partition, cacheValues, fieldsUsedInQuery);

                this.debug("Entity created and cached successfully: {}", pk);
            } else {
                this.debug("Entity creation failed for: {}", entity);
            }
            return createdEntity;
        });
    }

    public CompletableFuture<Boolean> update(T entity, LambdaQueryWrapper<T> key) {
        if (this.manager == null) {
            this.debug("Manager is null, cannot update");
            return CompletableFuture.completedFuture(false);
        }
        PartitionKey partition = partitionOf(key);
        return this.manager.get(key).thenCompose(oldEntity ->
                this.manager.update(entity, key).thenApply(success -> {
                    PartitionedKey<QueryKey> pkQueryKey = new PartitionedKey<>(partition, wrapQueryKey(new LambdaQueryWrapper<>(entity)));
                    if (success) {
                        // 更新缓存（以实体 pk 为准）
                        this.entityCache.put(pkQueryKey, entity);
                        this.debug("Entity updated in cache: {}", pkQueryKey);

                        if (oldEntity != null) {
                            invalidateRelatedEntityQueries(partition, oldEntity, entity);
                            // 失效由于 query 变化产生的相关分页
                            invalidatePagesByPartitionedQueryKey(new PartitionedKey<>(partition, wrapQueryKey(key)));
                        } else {
                            // 保险策略：失效 key 对应的分页
                            invalidatePagesByPartitionedQueryKey(new PartitionedKey<>(partition, wrapQueryKey(key)));
                        }
                    } else {
                        this.debug("Entity update failed for key: {}", pkQueryKey);
                    }
                    return success;
                })
        );
    }

    public CompletableFuture<Boolean> delete(LambdaQueryWrapper<T> key) {
        if (this.manager == null) {
            this.debug("Manager is null, cannot delete");
            return CompletableFuture.completedFuture(false);
        }
        PartitionKey partition = partitionOf(key);
        return this.manager.get(key).thenCompose(entityToDelete -> {
            if (entityToDelete == null) {
                this.debug("Entity not found for deletion, key: {}", wrapQueryKey(key));
                return CompletableFuture.completedFuture(false);
            }

            return this.manager.delete(key).thenApply(success -> {
                if (success) {
                    LambdaQueryWrapper<T> pkWrapper = new LambdaQueryWrapper<>(entityToDelete);
                    PartitionedKey<QueryKey> pkQueryKey = new PartitionedKey<>(partition, wrapQueryKey(pkWrapper));
                    this.entityCache.invalidate(pkQueryKey);
                    this.debug("Entity removed from cache: {}", pkQueryKey);
                    Map<String, Object> cacheValues = extractCacheKeyValuesByFieldName(entityToDelete);
                    Map<String, Map<Object, Set<PartitionedKey<QueryKey>>>> partitionIndex = fieldReverseIndex.get(partition);
                    if (partitionIndex != null) {
                        for (Map.Entry<String, Object> entry : cacheValues.entrySet()) {
                            String fieldName = entry.getKey();
                            Object value = entry.getValue();
                            if (value == null) continue;
                            Map<Object, Set<PartitionedKey<QueryKey>>> valueToQKs = partitionIndex.get(fieldName);
                            if (valueToQKs != null) {
                                Set<PartitionedKey<QueryKey>> relatedQueries = valueToQKs.remove(value);
                                if (relatedQueries != null) {
                                    for (PartitionedKey<QueryKey> qk : relatedQueries) {
                                        this.entityCache.invalidate(qk);
                                        this.debug("Removed entity cache for query {} (due to delete, field: {} value: {})", qk, fieldName, value);
                                        invalidatePagesByPartitionedQueryKey(qk);
                                    }
                                }
                                if (valueToQKs.isEmpty()) {
                                    partitionIndex.remove(fieldName);
                                }
                            }
                        }
                        if (partitionIndex.isEmpty()) {
                            fieldReverseIndex.remove(partition);
                        }
                    }

                    Object id = extractPrimaryKeyValue(entityToDelete);
                    if (id != null) {
                        Map<Object, Set<PartitionedKey<PageKey<QueryKey>>>> partitionEntityMap = entityIdToPages.get(partition);
                        if (partitionEntityMap != null) {
                            Set<PartitionedKey<PageKey<QueryKey>>> pages = partitionEntityMap.remove(id);
                            if (pages != null) {
                                for (PartitionedKey<PageKey<QueryKey>> pPageKey : pages) {
                                    this.pageCache.invalidate(pPageKey);
                                    removePageFromEntityIndex(partition, pPageKey);
                                    removePageFromQueryToPages(pPageKey);
                                    this.debug("Invalidated page {} because it contained entity id={}", pPageKey, id);
                                }
                            }
                            if (partitionEntityMap.isEmpty()) {
                                entityIdToPages.remove(partition);
                            }
                        }
                    }

                } else {
                    this.debug("Entity deletion failed for key: {}", wrapQueryKey(key));
                }
                return success;
            });
        });
    }

    private void triggerInvalidationByFields(PartitionKey partition, Map<String, Object> fieldValues, Set<String> fieldsUsedInQuery) {
        if (fieldValues == null || fieldValues.isEmpty()) return;
        Map<String, Map<Object, Set<PartitionedKey<QueryKey>>>> partitionIndex = fieldReverseIndex.get(partition);
        if (partitionIndex == null) return;
        for (String f : fieldValues.keySet()) {
            if (!fieldsUsedInQuery.isEmpty() && !fieldsUsedInQuery.contains(f)) continue;
            Object v = fieldValues.get(f);
            if (v == null) continue;
            Map<Object, Set<PartitionedKey<QueryKey>>> valueToQKs = partitionIndex.get(f);
            if (valueToQKs == null) continue;
            Set<PartitionedKey<QueryKey>> qks = valueToQKs.get(v);
            if (qks == null) continue;
            for (PartitionedKey<QueryKey> qk : qks) {
                invalidatePagesByPartitionedQueryKey(qk);
            }
        }
    }

    public CompletableFuture<PageResult<T>> getList(int pageNum, int pageSize, LambdaQueryWrapper<T> queryCondition) {
        PartitionKey partition = partitionOf(queryCondition);
        QueryKey queryKey = this.wrapQueryKey(queryCondition);
        PageKey<QueryKey> pageKey = new PageKey<>(pageNum, pageSize, queryKey);
        PartitionedKey<PageKey<QueryKey>> pPageKey = new PartitionedKey<>(partition, pageKey);
        PartitionedKey<QueryKey> pQueryKey = new PartitionedKey<>(partition, queryKey);

        PageResult<T> cached = this.pageCache.getIfPresent(pPageKey);
        if (cached != null) {
            this.debug("Page cache hit for pageKey: {}", pPageKey);
            return CompletableFuture.completedFuture(cached);
        }

        this.debug("Page cache miss for pageKey: {}, querying from DB", pPageKey);

        if (this.manager == null) {
            this.debug("Manager is null, returning empty page result");
            return CompletableFuture.completedFuture(PageResult.build(Collections.emptyList(), 0, 0, pageNum));
        }

        return this.manager.getList(pageNum, pageSize, queryCondition).thenApply(result -> {
            if (result == null) {
                this.debug("Query returned null result for pageKey: {}", pPageKey);
                return null;
            }

            this.pageCache.put(pPageKey, result);
            this.debug("Page cached for pageKey: {}, record count: {}", pPageKey, result.records().size());

            queryToPages.computeIfAbsent(pQueryKey, k -> ConcurrentHashMap.newKeySet()).add(pPageKey);
            this.debug("Added pageKey mapping for query condition: {} -> {}", pQueryKey, pPageKey);

            Set<String> fieldsUsedInQuery = this.extractFieldsUsedInQuery(queryCondition);
            int entityCachedCount = 0;
            for (T entity : result.records()) {

                LambdaQueryWrapper<T> entityPkWrapper = new LambdaQueryWrapper<>(entity);
                PartitionedKey<QueryKey> entityKey = new PartitionedKey<>(partition, this.wrapQueryKey(entityPkWrapper));
                this.entityCache.put(entityKey, entity);
                entityCachedCount++;

                Map<String, Object> cacheValues = extractCacheKeyValuesByFieldName(entity);
                recordQueryForCacheKeyValues(partition, pQueryKey, cacheValues, fieldsUsedInQuery);
            }

            recordPageEntities(partition, pPageKey, result.records());

            if (entityCachedCount > 0) {
                this.debug("Cached {} entities from page result", entityCachedCount);
            }

            return result;
        });
    }

    private void invalidatePagesByPartitionedQueryKey(PartitionedKey<QueryKey> pQueryKey) {
        Set<PartitionedKey<PageKey<QueryKey>>> relatedPages = queryToPages.remove(pQueryKey);
        if (relatedPages == null || relatedPages.isEmpty()) {
            this.debug("No related pages found for query key: {}", pQueryKey);
            return;
        }
        int invalidatedCount = 0;
        for (PartitionedKey<PageKey<QueryKey>> pPageKey : relatedPages) {
            this.pageCache.invalidate(pPageKey);
            removePageFromEntityIndex(pPageKey.partition(), pPageKey);
            invalidatedCount++;
            removePageFromQueryToPages(pPageKey);
        }
        this.debug("Invalidated {} pages for query key: {}", invalidatedCount, pQueryKey);
    }

    private void removePageFromQueryToPages(PartitionedKey<PageKey<QueryKey>> pPageKey) {
        if (pPageKey == null) return;
        queryToPages.forEach((qk, pages) -> {
            if (pages != null && pages.remove(pPageKey)) {
                if (pages.isEmpty()) {
                    queryToPages.remove(qk);
                }
            }
        });
    }

    private void invalidateRelatedEntityQueries(PartitionKey partition, T oldEntity, T newEntity) {
        Map<String, Object> oldValues = this.extractCacheKeyValuesByFieldName(oldEntity);
        Map<String, Object> newValues = this.extractCacheKeyValuesByFieldName(newEntity);

        Map<String, Map<Object, Set<PartitionedKey<QueryKey>>>> partitionIndex = fieldReverseIndex.get(partition);
        if (partitionIndex == null) return;

        for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
            String fieldName = entry.getKey();
            Object oldVal = entry.getValue();
            Object newVal = newValues.get(fieldName);

            boolean changed = !Objects.equals(oldVal, newVal);
            if (!changed) continue;
            if (oldVal == null) continue;

            Map<Object, Set<PartitionedKey<QueryKey>>> valueToQKs = partitionIndex.get(fieldName);
            if (valueToQKs != null) {
                Set<PartitionedKey<QueryKey>> relatedQueries = valueToQKs.remove(oldVal);
                if (relatedQueries != null) {
                    for (PartitionedKey<QueryKey> qk : relatedQueries) {
                        this.entityCache.invalidate(qk);
                        this.debug("Removed entity cache for query {} due to change of field {} value: {} -> {}", qk, fieldName, oldVal, newVal);
                        invalidatePagesByPartitionedQueryKey(qk);
                    }
                }
                if (valueToQKs.isEmpty()) {
                    partitionIndex.remove(fieldName);
                }
            }
        }
        if (partitionIndex.isEmpty()) {
            fieldReverseIndex.remove(partition);
        }
    }

    public void setExecutionMode(boolean value) {
        this.manager.setExecutionMode(value);
    }

    public boolean isAsync() {
        return this.manager.isAsync;
    }

    public void clearEntityCache() {
        long size = this.entityCache.estimatedSize();
        this.entityCache.invalidateAll();
        this.fieldReverseIndex.clear();
        this.entityIdToPages.clear();
        this.pageToEntityIds.clear();
        this.queryToPages.clear();
        this.debug("Cleared entity cache, removed approximately {} entries and cleared reverse indexes", size);
    }

    public void clearPageCache() {
        long pageCount = this.pageCache.estimatedSize();
        this.pageCache.invalidateAll();
        this.queryToPages.clear();
        this.pageToEntityIds.clear();
        this.entityIdToPages.clear();
        this.debug("Cleared page cache, removed approximately {} pages and cleared page indexes", pageCount);
    }

    public void clearAllCache() {
        long entityCount = this.entityCache.estimatedSize();
        long pageCount = this.pageCache.estimatedSize();
        this.clearEntityCache();
        this.clearPageCache();
        this.debug("Cleared all cache, removed approximately {} entities and {} pages", entityCount, pageCount);
    }

    public void debug(boolean status) {
        log.setDebug(status);
    }

}