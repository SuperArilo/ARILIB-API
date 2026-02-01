package com.tty.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.tty.api.enumType.FilePathEnum;
import com.tty.api.utils.FormatUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ConfigInstance {

    protected final Map<String, YamlConfiguration> CONFIGS = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

    public <E extends Enum<E> & FilePathEnum> String getValue(String keyPath, E filePath) {
        return getValue(keyPath, filePath, String.class, "null");
    }

    public <T, E extends Enum<E> & FilePathEnum> T getValue(String keyPath, E filePath, Class<T> tClass) {
        if (this.checkPath(keyPath)) return null;
        YamlConfiguration configuration = this.getObject(filePath.name());
        if (configuration == null) return null;
        return configuration.getObject(keyPath, tClass);
    }

    public <T, E extends Enum<E> & FilePathEnum> T getValue(String keyPath, E filePath, Type type, T defaultValue) throws JsonSyntaxException {
        if (checkPath(keyPath)) return defaultValue;

        YamlConfiguration fileConfiguration = this.getObject(filePath.name());
        if (fileConfiguration == null) return defaultValue;

        Object value = fileConfiguration.get(keyPath);
        if (value == null)  return defaultValue;

        if (value instanceof MemorySection) {
            YamlConfiguration tempConfig = new YamlConfiguration();
            FormatUtils.copySectionToYamlConfiguration((ConfigurationSection) value, tempConfig);
            return FormatUtils.yamlConvertToObj(tempConfig.saveToString(), type);
        } else {
            return this.gson.fromJson(this.gson.toJsonTree(value), type);
        }
    }

    public YamlConfiguration getObject(String fileName) {
        return CONFIGS.get(fileName);
    }

    private boolean checkPath(String path) {
        return path == null || path.isEmpty();
    }

    public <T extends Enum<T> & FilePathEnum> void setValue(JavaPlugin plugin, String topKeyPath, T filePath, Map<String, Object> values) throws IOException {

        YamlConfiguration configuration = this.getObject(filePath.name());
        if (configuration == null) throw new RuntimeException("Config file not found: " + filePath.name());

        values.forEach((k, v) -> configuration.set(topKeyPath + "." + k, v));
        setConfig(filePath.name(), configuration);

        configuration.save(new File(plugin.getDataFolder(), filePath.getPath()));

    }

    public void setConfig(String name, YamlConfiguration instance) {
        CONFIGS.put(name, instance);
    }

    public void clearConfigs() {
        CONFIGS.clear();
    }

}
