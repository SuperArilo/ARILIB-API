package com.tty.api.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tty.api.AbstractJavaPlugin;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public abstract class BaseConfiguration {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private final AbstractJavaPlugin plugin;

    @Setter
    @Getter
    private YamlConfiguration configuration;

    private final Cache<@NotNull String, Object> cache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(300, TimeUnit.MINUTES)
            .build();

    @Getter
    private final String path;

    public BaseConfiguration(AbstractJavaPlugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists()) {
            this.configuration = YamlConfiguration.loadConfiguration(file);
        }
    }

    public String getString(String keyPath) {
        if (this.configuration == null) return "null";
        return (String) cache.get(keyPath,
                k -> this.configuration.getString(k, "null"));
    }

    public boolean getBool(String keyPath, boolean defaultValue) {
        if (this.configuration == null) return defaultValue;
        return (Boolean) cache.get(keyPath, k -> this.configuration.getBoolean(k, defaultValue));
    }

    public int getInt(String keyPath, int defaultValue) {
        if (this.configuration == null) return defaultValue;
        return (Integer) cache.get(keyPath, k -> this.configuration.getInt(k, defaultValue));
    }

    public List<String> getStringList(String keyPath) {
        if (this.configuration == null) return List.of();
        return (List<String>) cache.get(keyPath, k -> this.configuration.getStringList(k));
    }

    public <T> T getValue(String keyPath, Type type, T defaultValue) {
        if (keyPath == null || keyPath.isEmpty()) return defaultValue;
        if (this.configuration == null) return defaultValue;
        String cacheKey = keyPath + "::" + type.getTypeName();
        return (T) cache.get(cacheKey, k -> {
            Object value = this.configuration.get(keyPath);
            if (value == null) return defaultValue;
            try {
                if (value instanceof MemorySection section) {
                    return GSON.fromJson(GSON.toJson(sectionToMap(section)), type);
                } else {
                    return GSON.fromJson(GSON.toJsonTree(value), type);
                }
            } catch (Exception e) {
                this.plugin.getLog().warn(e,
                        "config conversion failed at path {}. Value type: {}, Value: {}, target type: {}",
                        keyPath,
                        value.getClass().getName(),
                        value,
                        type.getTypeName());
                return defaultValue;
            }
        });
    }

    public <S> void setValue(String path, Map<String, S> values) {
        if (this.configuration == null) throw new NullPointerException("configuration file not found.");
        values.forEach((k, v) -> {
            Object valueToSave = v;
            if (!(v instanceof Map) && !(v instanceof String) && !(v instanceof Number) && !(v instanceof Boolean)) {
                try {
                    valueToSave = GSON.fromJson(GSON.toJson(v),
                            new TypeToken<Map<String, Object>>(){}.getType());
                } catch (JsonSyntaxException e) {
                    this.plugin.getLog().warn(e, "gson json syntax error for key {}", k);
                } catch (Exception e) {
                    this.plugin.getLog().warn(e, "unexpected error converting key {}", k);
                }
            }
            this.configuration.set(path + "." + k, valueToSave);
        });
        this.cache.invalidateAll();
        this.plugin.getScheduler().runAsync(i -> {
            try {
                this.configuration.save(new File(this.plugin.getDataFolder(), this.path));
            } catch (IOException e) {
                this.plugin.getLog().error(e, "save file {} error, key path {}.", this.path, path);
            }
        });
    }

    public boolean isEmpty() {
        return this.configuration == null;
    }

    private static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                result.put(key, sectionToMap((ConfigurationSection) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    public void save() throws IOException {
        this.configuration.save(new File(this.plugin.getDataFolder(), this.path));
    }

    public void clearCache() {
        this.cache.invalidateAll();
    }

}