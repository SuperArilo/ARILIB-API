package com.tty.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tty.api.enumType.FilePathEnum;
import com.tty.api.event.WhenPluginConfigReloadCompleteEvent;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ConfigInstance {

    private final AbstractJavaPlugin plugin;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final String DOWNLOAD_URL = "https://raw.githubusercontent.com/SuperArilo/Plugin-Configs/refs/heads/main/";

    private final Map<FilePathEnum, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

    private final LoaderOptions options;

    public ConfigInstance(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowRecursiveKeys(true);
        loaderOptions.setAllowDuplicateKeys(false);
        this.options = loaderOptions;
    }

    public <T, E extends Enum<E> & FilePathEnum> T getValue(String keyPath, E filePath, Type type, T defaultValue) {
        if (this.checkPath(keyPath)) return defaultValue;
        YamlConfiguration fileConfiguration = this.getObject(filePath);
        if (fileConfiguration == null) return defaultValue;
        Object value = fileConfiguration.get(keyPath);
        if (value == null) return defaultValue;
        try {
            if (value instanceof MemorySection section) {
                return this.gson.fromJson(this.gson.toJson(this.sectionToMap(section)), type);
            } else {
                return this.gson.fromJson(this.gson.toJsonTree(value), type);
            }
        } catch (Exception e) {
            this.plugin.getLog().warn(e, "config conversion failed at path {}. Value type: {}, Value: {}, target type: {}",
                    keyPath,
                    value.getClass().getName(),
                    value,
                    type.getTypeName());
        }
        return defaultValue;
    }

    public <E extends Enum<E> & FilePathEnum> String getValue(String keyPath, E filePath) {
        return this.getValue(keyPath, filePath, String.class, "null");
    }

    public <T, E extends Enum<E> & FilePathEnum> T getValue(String keyPath, E filePath, Class<T> tClass) {
        return this.getValue(keyPath, filePath, tClass, null);
    }

    //只能传简单的基元
    public <T, E extends Enum<E> & FilePathEnum> T getValue(String keyPath, E filePath, Class<T> tClass, T defaultValue) {
        if (this.checkPath(keyPath)) return defaultValue;
        YamlConfiguration configuration = this.getObject(filePath);
        if (configuration == null) return defaultValue;
        return configuration.getObject(keyPath, tClass, defaultValue);
    }

    public YamlConfiguration getObject(FilePathEnum pathEnum) {
        return this.configs.get(pathEnum);
    }

    private boolean checkPath(String path) {
        return path == null || path.isEmpty();
    }

    public <T extends Enum<T> & FilePathEnum, S> void setValue(String path, T filePath, Map<String, S> values) throws IOException {
        YamlConfiguration configuration = this.getObject(filePath);
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
        this.configs.put(filePath, configuration);
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

    public synchronized void saveAllFiles() {
        String langType = this.plugin.getConfig().getString("lang", "cn");
        this.configs.forEach((k, v) -> {
            File file = new File(this.plugin.getDataFolder(), k.getPath().replace("[lang]", langType));
            if (file.exists()) return;
            try {
                v.save(file);
            } catch (IOException e) {
                this.plugin.getLog().error(e);
            }
        });
        this.clearConfigs();
    }

    public synchronized void clearConfigs() {
        this.configs.clear();
    }

    public synchronized void reload(FilePathEnum[] pathList, @Nullable CommandSender sender) {
        String langType = this.plugin.getConfig().getString("lang", "cn");
        if (sender == null) {
            for (FilePathEnum pathEnum : pathList) {
                String path = pathEnum.getPath().replace("[lang]", langType);
                File file = new File(this.plugin.getDataFolder(), path);
                if (file.exists() && !this.plugin.getConfig().getBoolean("debug.overwrite-file", false)) {
                    this.loadAndSet(pathEnum, file);
                    continue;
                }
                try {
                    this.plugin.saveResource(path, true);
                    this.loadAndSet(pathEnum, file);
                } catch (Exception ex) {
                    String url = DOWNLOAD_URL + this.plugin.getName() + "/" + Arrays.stream(path.split("/")).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).collect(Collectors.joining("/"));
                    this.downloadFileSync(url, file, pathEnum);
                }
            }
            if (this.plugin.isEnabled()) {
                this.plugin.getScheduler().run(this.plugin, i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, null)));
            }
            return;
        }

        this.clearConfigs();
        AtomicInteger pendingDownloads = new AtomicInteger(0);
        boolean hasDownloads = false;

        for (FilePathEnum pathEnum : pathList) {
            String path = pathEnum.getPath().replace("[lang]", langType);
            File file = new File(this.plugin.getDataFolder(), path);

            if (file.exists() && !this.plugin.getConfig().getBoolean("debug.overwrite-file", false)) {
                this.loadAndSet(pathEnum, file);
                continue;
            }

            try {
                this.plugin.saveResource(path, true);
                this.loadAndSet(pathEnum, file);
            } catch (Exception ex) {
                hasDownloads = true;
                pendingDownloads.incrementAndGet();

                String url = DOWNLOAD_URL + this.plugin.getName() + "/" + Arrays.stream(path.split("/")).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).collect(Collectors.joining("/"));
                File target = new File(this.plugin.getDataFolder(), path);

                this.downloadFile(url, target,
                        () -> {
                            this.loadAndSet(pathEnum, target);
                            this.onDownloadComplete(pendingDownloads, sender);
                        },
                        () -> {
                            this.plugin.getLog().warn("could not download file: {}", path);
                            this.onDownloadComplete(pendingDownloads, sender);
                        }
                );
            }
        }

        if (!hasDownloads && this.plugin.isEnabled()) {
            this.plugin.getScheduler().run(this.plugin, i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
        }
    }

    private void onDownloadComplete(AtomicInteger counter, @Nullable CommandSender sender) {
        if (counter.decrementAndGet() == 0 && this.plugin.isEnabled()) {
            this.plugin.getScheduler().run(this.plugin, i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
        }
    }

    private synchronized void loadAndSet(FilePathEnum pathEnum, File file) {
        this.configs.put(pathEnum, YamlConfiguration.loadConfiguration(file));
    }

    public void downloadFile(String url, File targetFile, @Nullable Runnable onSuccess, @Nullable Runnable onError) {
        Request request = new Request.Builder().header("User-Agent", "PaperMC-Plugin").url(url).build();
        this.client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                plugin.getLog().warn(e, "download file error, url: {}. you can execute command /{} reload to download again.", url, plugin.getName());
                if (onError != null) {
                    onError.run();
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        plugin.getLog().warn("download file error, code: {}. you can execute command /{} reload to download again.", response.code(), plugin.getName());
                        if (onError != null) onError.run();
                        return;
                    }
                    targetFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(response.body().bytes());
                    }
                    plugin.getLog().debug("file save to {}", targetFile.getPath());
                    if (onSuccess != null) onSuccess.run();
                } catch (IOException e) {
                    plugin.getLog().warn(e, "file save error. path: {}", targetFile.getPath());
                    if (onError != null) onError.run();
                }
            }
        });
    }

    private void downloadFileSync(String url, File targetFile, FilePathEnum pathEnum) {
        Request request = new Request.Builder()
                .header("User-Agent", "PaperMC-Plugin")
                .url(url)
                .build();
        this.plugin.getLog().info("start download file, url: {}", url);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                this.plugin.getLog().warn("download file error. code: {}", response.code());
                return;
            }
            targetFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(response.body().bytes());
            }
            this.plugin.getLog().debug("file save to {}", targetFile.getPath());
            this.loadAndSet(pathEnum, targetFile);
        } catch (IOException e) {
            this.plugin.getLog().warn(e, "file download/save error, url: {}. you can execute command /{} reload to download again." , url, this.plugin.getName());
        }
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section) {
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

}