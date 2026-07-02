package com.tty.api;

import com.tty.api.dto.TempRegisterService;
import com.tty.api.configuration.BaseConfiguration;
import com.tty.api.utils.VersionUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public abstract class AbstractJavaPlugin extends JavaPlugin {

    @Getter
    private boolean debug = false;

    @Getter
    private ConfigurationManager configurationManager;

    @Getter
    private Log log;

    @Getter
    private final Scheduler scheduler = Scheduler.create();

    @Getter
    private Executor executorSync;

    @Getter
    private Executor executorAsync;

    @Getter
    private NbtManager nbtManager;

    @Getter
    private ComponentTool componentTool;

    @Override
    public void onLoad() {
        this.log = new Log(this);
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
        this.executorSync = task -> this.scheduler.run(this, i -> task.run());
        this.executorAsync = task -> this.scheduler.runAsync(this, i -> task.run());

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
        this.nbtManager = new NbtManager(this);
        this.enabling();
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

    public void doReloadAllFiles(@Nullable CommandSender sender) {
        this.saveDefaultConfig();
        this.reloadConfig();
        this.debug = this.getConfig().getBoolean("debug.enable", false);
        this.log.setDebug(this.debug);
        if (this.configurationManager == null) {
            this.configurationManager = new ConfigurationManager(this);
        }
        this.configurationManager.reload(this.configurations(), sender);
    }

}
