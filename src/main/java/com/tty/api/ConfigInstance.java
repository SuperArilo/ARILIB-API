package com.tty.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tty.api.enumType.FilePathEnum;
import com.tty.api.utils.FormatUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ConfigInstance {

    private final BaseJavaPlugin plugin;
    private final FilePathEnum[] pathList;

    private final Map<String, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

    public ConfigInstance(BaseJavaPlugin plugin, FilePathEnum[] pathList) {
        this.plugin = plugin;
        this.pathList = pathList;
        this.reload();
    }

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
        if (this.checkPath(keyPath)) return defaultValue;

        YamlConfiguration fileConfiguration = this.getObject(filePath.name());
        if (fileConfiguration == null) return defaultValue;

        Object value = fileConfiguration.get(keyPath, defaultValue);

        if (value == null) return null;

        if (value instanceof MemorySection) {
            YamlConfiguration tempConfig = new YamlConfiguration();
            FormatUtils.copySectionToYamlConfiguration((ConfigurationSection) value, tempConfig);
            return FormatUtils.yamlConvertToObj(tempConfig.saveToString(), type);
        } else {
            try {
                return this.gson.fromJson(this.gson.toJsonTree(value), type);
            } catch (Exception e) {
                this.plugin.getLog().warn(e, "config conversion failed at path {}. Value type: {}, Value: {}, target type: {}",
                        keyPath,
                        value.getClass().getName(),
                        value,
                        type.getTypeName());
            }
        }
        return defaultValue;
    }

    public YamlConfiguration getObject(String fileName) {
        return this.configs.get(fileName);
    }

    private boolean checkPath(String path) {
        return path == null || path.isEmpty();
    }

    public <T extends Enum<T> & FilePathEnum, S> void setValue(JavaPlugin plugin, String topKeyPath, T filePath, Map<String, S> values) throws IOException {

        YamlConfiguration configuration = this.getObject(filePath.name());
        if (configuration == null) throw new NullPointerException("Config file not found: " + filePath.name());

        values.forEach((k, v) -> {
            Object valueToSave = v;
            if (!(v instanceof Map) && !(v instanceof String) && !(v instanceof Number) && !(v instanceof Boolean)) {
                try {
                    valueToSave = this.gson.fromJson(this.gson.toJson(v), new TypeToken<Map<String, Object>>(){}.getType());
                } catch (JsonSyntaxException e) {
                    this.plugin.getLog().warn(e, "gson json syntax error for key {}", k);
                } catch (Exception e) {
                    this.plugin.getLog().warn(e, "unexpected error converting key {}", k);
                }
            }
            configuration.set(topKeyPath + "." + k, valueToSave);
        });

        this.setConfig(filePath.name(), configuration);
        configuration.save(new File(plugin.getDataFolder(), filePath.getPath()));
    }

    public void setConfig(String name, YamlConfiguration instance) {
        this.configs.put(name, instance);
    }

    public void clearConfigs() {
        this.configs.clear();
    }

    public void reload() {
        this.clearConfigs();
        FileConfiguration pluginConfig = this.plugin.getConfig();
        for (FilePathEnum pathEnum : this.pathList) {
            String replace = pathEnum.getPath().replace("[lang]", this.plugin.getConfig().getString("lang", "cn"));
            File file = new File(this.plugin.getDataFolder(), replace);
            if (!file.exists()) {
                this.plugin.saveResource(replace, true);
            } else if (pluginConfig.getBoolean("debug.overwrite-file", false)) {
                try {
                    this.plugin.saveResource(replace, true);
                } catch (Exception e) {
                    this.plugin.getLog().warn(e, "can not find file {}, path: {}", pathEnum.getNickName(), pathEnum.getPath());
                }
            }
            this.setConfig(pathEnum.name(), YamlConfiguration.loadConfiguration(file));
        }
    }

}
