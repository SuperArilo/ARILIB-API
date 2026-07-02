package com.tty.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tty.api.event.WhenPluginConfigReloadCompleteEvent;
import com.tty.api.configuration.BaseConfiguration;
import com.tty.api.configuration.AllowDownloadConfiguration;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
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
        if (configuration == null) throw new NullPointerException("could not found configuration.");
        return (T) configuration;
    }

    public <T> T deepCopy(T obj, Type typeOfT) {
        return this.gson.fromJson(this.gson.toJson(obj), typeOfT);
    }

    public <T> T yamlConvertToObj(String rawYamlString, Type type) {
        Object intermediateObj = new Yaml(this.loaderOptions).load(rawYamlString);
        if (intermediateObj instanceof Map || intermediateObj instanceof List) {
            return this.gson.fromJson(this.gson.toJson(intermediateObj), type);
        }
        return this.gson.fromJson(this.gson.toJsonTree(intermediateObj), type);
    }

    protected void reload(@Nullable List<BaseConfiguration> list, @Nullable CommandSender sender) {
        this.configurationMap.clear();
        boolean overwrite = this.plugin.getConfig().getBoolean("debug.overwrite-file");
        if (list == null) return;
        AtomicInteger count = new AtomicInteger(0);
        Runnable onAllAsyncDone = () -> {
            if (count.decrementAndGet() == 0) {
                this.plugin.getScheduler().run(this.plugin, i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
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
            this.plugin.getScheduler().run(this.plugin, i -> Bukkit.getServer().getPluginManager().callEvent(new WhenPluginConfigReloadCompleteEvent(this.plugin, sender)));
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

    public synchronized void saveAllFiles() {
        String langType = this.plugin.getConfig().getString("lang", "cn");
        this.configurationMap.forEach((k, v) -> {
            try {
                v.save();
            } catch (IOException e) {
                this.plugin.getLog().error(e);
            }
        });
    }

}
