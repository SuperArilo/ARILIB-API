package com.tty.api;

import com.google.gson.JsonParser;
import com.tty.api.dto.PluginVersion;
import com.tty.api.dto.TempRegisterService;
import com.tty.api.configuration.BaseConfiguration;
import com.tty.api.state.StateService;
import com.tty.api.utils.VersionUtil;
import lombok.Getter;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class AbstractJavaPlugin extends JavaPlugin {

    @Getter
    private boolean debug = false;

    @Getter
    private PluginVersion version;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    @Getter
    private ConfigurationManager configurationManager;

    @Getter
    private Log log;

    @Getter
    private Scheduler scheduler;

    @Getter
    private Executor executorSync;

    @Getter
    private Executor executorAsync;

    @Getter
    private NbtManager nbtManager;

    @Getter
    private ComponentTool componentTool;

    @Getter
    private StatusManager statusManager;

    @Override
    public void onLoad() {
        this.log = new Log(this);
        this.scheduler = Scheduler.create(this);
        this.loading();
    }

    @Override
    public void onEnable() {
        if(VersionUtil.isServerVersionLowerThan("1.21")) {
            log.error("server version is too low. This plugin requires at least 1.21. Disabling plugin...");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.doReloadAllFiles(null);

        this.componentTool = new ComponentTool(this);
        this.executorSync = task -> this.scheduler.run(i -> task.run());
        this.executorAsync = task -> this.scheduler.runAsync(i -> task.run());

        for (Listener event : this.registerEvents()) {
            Bukkit.getPluginManager().registerEvents(event, this);
        }
        for (TempRegisterService<?> temp : this.loadOtherPlugin()) {
            @SuppressWarnings("unchecked") Consumer<Object> consumer = (Consumer<Object>) temp.consumer();
            if (Bukkit.getPluginManager().isPluginEnabled(temp.pluginName())) {
                RegisteredServiceProvider<?> registration = Bukkit.getServer().getServicesManager().getRegistration(temp.tClass());
                if (registration != null) {
                    consumer.accept(registration.getProvider());
                } else {
                    this.log.warn("failed to load class {}.", temp.pluginName());
                    consumer.accept(null);
                }
            } else {
                this.log.warn("failed to load class {}. because {} not found.", temp.pluginName());
                consumer.accept(null);
            }
        }

        this.enabling();

        this.nbtManager = new NbtManager(this);
        this.statusManager = new StatusManager();
        this.statusManager.registerStateMachine(this.services());

        this.checkUpdate();
    }

    @Override
    public void onDisable() {
        this.configurationManager.saveAllFiles();
        this.disabling();
    }

    /**
     * 插件 load 阶段
     */
    protected abstract void loading();

    /**
     * 插件 enable 阶段
     */
    protected abstract void enabling();

    /**
     * 插件 disable 阶段
     */
    protected abstract void disabling();

    /**
     * 批量加载其它插件
     * @return 插件列表类
     */
    protected abstract List<TempRegisterService<?>> loadOtherPlugin();

    /**
     * 批量注册事件
     * @return 事件集合
     */
    @NotNull protected abstract List<Listener> registerEvents();

    @Nullable protected abstract List<BaseConfiguration> configurations();

    @Nullable protected abstract List<StateService<?>> services();

    public void doReloadAllFiles(@Nullable CommandSender sender) {
        this.saveDefaultConfig();
        this.reloadConfig();

        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (stream == null) return;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
                double defaultVersion = defaultConfig.getDouble("version", 1.0);
                double currentVersion = this.getConfig().getDouble("version", 0);
                if (currentVersion < defaultVersion) {
                    this.getLog().info("Your config.yml is outdated (v{}). Please delete it to regenerate the latest version (v{})", currentVersion, defaultVersion);
                }
            }
        } catch (IOException e) {
            this.getLog().error(e);
            return;
        }

        this.debug = this.getConfig().getBoolean("debug.enable", false);
        this.log.setDebug(this.debug);
        if (this.configurationManager == null) {
            this.configurationManager = new ConfigurationManager(this);
        }
        this.configurationManager.reload(this.configurations(), sender);
        if (sender != null) {
            this.checkUpdate();
        }
    }

    @SuppressWarnings({"deprecation", "UnstableApiUsage"})
    private CompletableFuture<PluginVersion> requestVersion() {
        CompletableFuture<PluginVersion> future = new CompletableFuture<>();
        Log log = this.getLog();
        PluginVersion version = new PluginVersion();

        if (Scheduler.isFolia()) {
            version.setCurrentVersion(this.getPluginMeta().getVersion());
        } else {
            version.setCurrentVersion(this.getDescription().getVersion());
        }

        String apiUrl = "https://api.github.com/repos/SuperArilo/" + this.getName().toUpperCase() + "/releases/latest";
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        this.httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                log.error("Failed to check for updates (network/timeout)", e);
                future.complete(version);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        if (response.code() == 429) {
                            log.warn("github api rate limit exceeded, please try again later.");
                        } else {
                            log.warn("update check failed, http status code: {}", response.code());
                        }
                    } else {
                        String body = response.body().string();
                        if (body.isEmpty()) {
                            log.warn("github api returned empty response");
                        } else {
                            String tag = JsonParser.parseString(body).getAsJsonObject().get("tag_name").getAsString();
                            version.setRemoteVersion(tag.startsWith("v") ? tag.substring(1) : tag);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error parsing update response", e);
                } finally {
                    future.complete(version);
                }
            }
        });

        return future;
    }

    public void checkUpdate() {
        this.requestVersion().thenAcceptAsync(s -> {
            this.version = s;
            if (s.hasNewVersion()) {
                log.info("=========================================");
                log.info("new version available: ", s.getRemoteVersion());
                log.info("current version: " + s.getCurrentVersion());
                log.info("=========================================");
            } else {
                log.info("plugin is up to date now.");
            }
        }).exceptionallyAsync(i -> {
            this.getLog().error(i);
            return null;
        });
    }

}
