package com.tty.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tty.api.enumType.FilePathEnum;
import com.tty.api.event.WhenPluginConfigReloadCompleteEvent;
import com.tty.api.utils.FormatUtils;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class ConfigInstance {

    private final AbstractJavaPlugin plugin;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final String DOWNLOAD_URL = "https://raw.githubusercontent.com/SuperArilo/Plugin-Lang/refs/heads/main/";

    private final Map<String, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

    private final LoaderOptions options;

    public ConfigInstance(AbstractJavaPlugin plugin) {
        this.plugin = plugin;

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowRecursiveKeys(true);
        loaderOptions.setAllowDuplicateKeys(false);
        this.options = loaderOptions;
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
            return this.yamlConvertToObj(tempConfig.saveToString(), type);
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

    public <E extends Enum<E> & FilePathEnum> String getValue(String keyPath, E filePath) {
        return this.getValue(keyPath, filePath, String.class, "null");
    }

    public <T, E extends Enum<E> & FilePathEnum> T getValue(String keyPath, E filePath, Class<T> tClass) {
        if (this.checkPath(keyPath)) return null;
        YamlConfiguration configuration = this.getObject(filePath.name());
        if (configuration == null) return null;
        return configuration.getObject(keyPath, tClass);
    }

    public YamlConfiguration getObject(String fileName) {
        return this.configs.get(fileName);
    }

    private boolean checkPath(String path) {
        return path == null || path.isEmpty();
    }

    public <T extends Enum<T> & FilePathEnum, S> void setValue(String path, T filePath, Map<String, S> values) throws IOException {

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
            configuration.set(path + "." + k, valueToSave);
        });

        this.setConfig(filePath.name(), configuration);
        configuration.save(new File(plugin.getDataFolder(), filePath.getPath()));
    }

    public <T> T deepCopy(T obj, Type typeOfT) {
        return this.gson.fromJson(this.gson.toJson(obj), typeOfT);
    }

    public <T> T yamlConvertToObj(String rawYamlString, Type type) {
        Object intermediateObj = new Yaml(this.options).load(rawYamlString);
        if (intermediateObj instanceof Map || intermediateObj instanceof List) {
            return this.gson.fromJson(this.gson.toJson(intermediateObj), type);
        }
        return this.gson.fromJson(this.gson.toJsonTree(intermediateObj), type);
    }

    public void setConfig(String name, YamlConfiguration instance) {
        this.configs.put(name, instance);
    }

    public void clearConfigs() {
        this.configs.clear();
    }

    public void reload(FilePathEnum[] pathList, @Nullable CommandSender sender) {
        this.clearConfigs();
        String langType = this.plugin.getConfig().getString("lang", "cn");
        AtomicInteger pending = new AtomicInteger(0);
        AtomicBoolean loopDone = new AtomicBoolean(false);
        Runnable onAllComplete = () -> {
            if (pending.decrementAndGet() == 0 && loopDone.get()) {
                this.plugin.getScheduler().run(this.plugin, i ->
                        Bukkit.getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
            }
        };

        for (FilePathEnum pathEnum : pathList) {
            String path = pathEnum.getPath().replace("[lang]", langType);
            File file = new File(this.plugin.getDataFolder(), path);

            boolean exists = file.exists();
            boolean overwrite = this.plugin.getConfig().getBoolean("debug.overwrite-file", false);

            if (!exists || overwrite) {
                try {
                    this.plugin.saveResource(path, true);
                    this.loadAndSet(pathEnum, file);
                } catch (Exception e) {
                    pending.incrementAndGet();
                    this.plugin.getLog().info("could not find file {}, start download.", path);
                    String[] segments = path.split("/");
                    String encodedPath = java.util.Arrays.stream(segments)
                            .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
                            .collect(Collectors.joining("/"));
                    String url = DOWNLOAD_URL + this.plugin.getName() + "/" + encodedPath;
                    File langTargetFile = new File(this.plugin.getDataFolder(), path);
                    this.downloadFile(url, langTargetFile,
                            () -> {
                                this.loadAndSet(pathEnum, langTargetFile);
                                onAllComplete.run();
                            },
                            () -> {
                                this.plugin.getLog().warn("could not download lang file {}.", path);
                                onAllComplete.run();
                            });
                }
            } else {
                this.loadAndSet(pathEnum, file);
            }
        }
        loopDone.set(true);
        if (pending.get() == 0) {
            this.plugin.getScheduler().run(this.plugin, i ->
                    Bukkit.getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
        }
    }

    private synchronized void loadAndSet(FilePathEnum pathEnum, File file) {
        this.setConfig(pathEnum.name(), YamlConfiguration.loadConfiguration(file));
    }

    public void downloadFile(String url, File targetFile, @Nullable Runnable onSuccess, @Nullable Runnable onError) {
        AbstractJavaPlugin javaPlugin = this.plugin;
        Request request = new Request.Builder().header("User-Agent", "PaperMC-Plugin").url(url).build();
        this.client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                javaPlugin.getLog().warn(e,"download file error. url: {} ", url);
                if (onError != null) {
                    onError.run();
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        javaPlugin.getLog().warn("download file error. code: {}", response.code());
                        if (onError != null) onError.run();
                        return;
                    }
                    targetFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(response.body().bytes());
                    }
                    javaPlugin.getLog().debug("file save to {}", targetFile.getPath());
                    if (onSuccess != null) onSuccess.run();
                } catch (IOException e) {
                    javaPlugin.getLog().warn(e, "file save error. path: {}", targetFile.getPath());
                    if (onError != null) onError.run();
                }
            }
        });
    }

}
