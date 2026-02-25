package com.tty.api.annotations.cache;

import java.lang.annotation.*;

/**
 * 标记实体字段作为缓存唯一键
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheKey { }
