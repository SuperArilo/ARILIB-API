package com.tty.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.tty.api.configuration.AllowDownloadConfiguration;
import com.tty.api.configuration.BaseConfiguration;
import com.tty.api.event.WhenPluginConfigReloadCompleteEvent;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigurationManager {

    private final AbstractJavaPlugin plugin;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    private final LoaderOptions loaderOptions;

    private final Map<Class<? extends BaseConfiguration>, BaseConfiguration> configurationMap = new ConcurrentHashMap<>();

    public ConfigurationManager(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowRecursiveKeys(true);
        loaderOptions.setAllowDuplicateKeys(false);
        this.loaderOptions = loaderOptions;
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseConfiguration> T get(Class<T> tClass) {
        BaseConfiguration configuration = this.configurationMap.get(tClass);
        if (configuration == null) {
            try {
                Constructor<T> matched = null;
                for (Constructor<?> ctor : tClass.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == 1) {
                        Class<?> paramType = ctor.getParameterTypes()[0];
                        if (this.plugin == null || paramType.isAssignableFrom(this.plugin.getClass())) {
                            matched = (Constructor<T>) ctor;
                            break;
                        }
                    }
                }
                if (matched == null) {
                    throw new NoSuchMethodException("No constructor compatible with " + this.plugin);
                }
                configuration = matched.newInstance(this.plugin);
                this.configurationMap.put(tClass, configuration);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate " + tClass.getName(), e);
            }
        }
        return (T) configuration;
    }

    public @Nullable <T> T deepCopy(T obj, Type typeOfT) {
        try {
            return this.gson.fromJson(this.gson.toJson(obj), typeOfT);
        } catch (JsonSyntaxException e) {
            this.plugin.getLog().error(e);
            return null;
        }

    }

    public <T> T yamlConvertToObj(String rawYamlString, Type type) {
        Object intermediateObj = new Yaml(this.loaderOptions).load(rawYamlString);
        if (intermediateObj instanceof Map || intermediateObj instanceof List) {
            try {
                return this.gson.fromJson(this.gson.toJson(intermediateObj), type);
            } catch (Exception e) {
                this.plugin.getLog().error(e, "could not convent type {}", type.getTypeName());
                return null;
            }
        } else {
            return this.gson.fromJson(this.gson.toJsonTree(intermediateObj), type);
        }

    }

    protected void reload(@Nullable List<BaseConfiguration> list, @Nullable CommandSender sender) {
        this.configurationMap.clear();
        boolean overwrite = this.plugin.getConfig().getBoolean("debug.overwrite-file");
        if (list == null) return;
        AtomicInteger count = new AtomicInteger(0);
        Runnable onAllAsyncDone = () -> {
            if (count.decrementAndGet() == 0) {
                this.plugin.getScheduler().run(i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
            }
        };
        for (BaseConfiguration configuration : list) {
            configuration.clearCache();
            if (overwrite) {
                try {
                    this.plugin.saveResource(configuration.getPath(), true);
                } catch (Exception e) {
                    if (!(configuration instanceof AllowDownloadConfiguration download)) {
                        this.plugin.getLog().error(e, "could not overwrite file {}. because not found in jar.", configuration.getPath());
                        continue;
                    }
                    if (sender == null) {
                        this.downloadSync(download);
                    } else {
                        count.incrementAndGet();
                        this.downloadAsync(download, onAllAsyncDone);
                    }
                }
            } else {
                if (!configuration.isEmpty()) {
                    this.configurationMap.put(configuration.getClass(), configuration);
                    if (configuration instanceof AllowDownloadConfiguration download) {
                        this.checkRemoteVersionAsync(download);
                    }
                } else if (configuration instanceof AllowDownloadConfiguration download) {
                    if (sender == null) {
                        this.downloadSync(download);
                    } else {
                        count.incrementAndGet();
                        this.downloadAsync(download, onAllAsyncDone);
                    }
                }
            }
        }
        if (count.get() == 0) {
            this.plugin.getScheduler().run(i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
        }
    }

    private void downloadSync(AllowDownloadConfiguration download) {
        File targetFile = new File(plugin.getDataFolder(), download.getPath());
        Request request = new Request.Builder().url(download.getUrl()).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                plugin.getLog().warn("download file error. code: {}", response.code());
                return;
            }
            targetFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(response.body().bytes());
            }
            download.setConfiguration(YamlConfiguration.loadConfiguration(targetFile));
            this.configurationMap.put(download.getClass(), download);
            plugin.getLog().debug("file save to {}", targetFile);
        } catch (IOException e) {
            plugin.getLog().warn(e, "file download/save error, url: {}", download.getUrl());
        }
    }

    private void downloadAsync(AllowDownloadConfiguration download, Runnable onComplete) {
        File targetFile = new File(plugin.getDataFolder(), download.getPath());
        Request request = new Request.Builder().header("User-Agent", "PaperMC-Plugin").url(download.getUrl()).build();
        this.client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                plugin.getLog().warn(e, "download file error, url: {}", download.getUrl());
                onComplete.run();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        plugin.getLog().warn("download file error, code: {}", response.code());
                        return;
                    }
                    targetFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(response.body().bytes());
                    }
                    download.setConfiguration(YamlConfiguration.loadConfiguration(targetFile));
                    configurationMap.put(download.getClass(), download);
                    plugin.getLog().debug("file save to {}", targetFile);
                } catch (IOException e) {
                    plugin.getLog().warn(e, "file save error. path: {}", targetFile);
                } finally {
                    onComplete.run();
                }
            }
        });
    }

    private void checkRemoteVersionAsync(AllowDownloadConfiguration download) {
        Request request = new Request.Builder()
                .url(download.getUrl())
                .header("User-Agent", "PaperMC-Plugin")
                .build();

        this.client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                plugin.getLog().error(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        plugin.getLog().warn("check update error, HTTP {} : {}", response.code(), download.getUrl());
                        return;
                    }

                    long contentLength = response.body().contentLength();
                    if (contentLength > 1024 * 1024) {
                        plugin.getLog().warn("check file error, file is too large: {}", contentLength, download.getUrl());
                        return;
                    }

                    double remote = 0;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("version:")) {
                                String value = trimmed.substring("version:".length()).trim();
                                if (value.startsWith("\"") && value.endsWith("\"")) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                try {
                                    remote = Double.parseDouble(value);
                                } catch (NumberFormatException ignored) {}
                                break;
                            }
                        }
                    }

                    double local = download.getVersion();
                    if (remote > local) {
                        plugin.getLog().info("can update - file: {}  local: {}  remote: {}", download.getPath(), local, remote);
                    }
                } catch (IOException e) {
                    plugin.getLog().error(e);
                }
            }
        });
    }


    public synchronized void saveAllFiles() {
        this.configurationMap.forEach((k, v) -> {
            try {
                v.save();
            } catch (IOException e) {
                this.plugin.getLog().error(e);
            }
        });
    }

}
