package com.tty.api;

import com.tty.api.enumType.FilePathEnum;
import com.tty.api.utils.VersionUtil;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class BaseJavaPlugin extends JavaPlugin {

    @Getter
    private boolean debug = false;
    @Getter
    private ConfigInstance configInstance;
    @Getter
    private Log log;
    @Getter
    private final Scheduler scheduler = Scheduler.create();

    @Override
    public void onLoad() {
        this.log = new Log(this);
        this.doReloadAllFiles();
    }

    @Override
    public void onEnable() {
        if(VersionUtil.isServerVersionLowerThan("1.21.3")) {
            log.error("server version is too low. This plugin requires at least 1.21.3. Disabling plugin...");
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.configInstance != null) {
            this.configInstance.clearConfigs();
        }
    }

    protected abstract FilePathEnum[] fileList();

    public void doReloadAllFiles() {
        this.saveDefaultConfig();
        this.reloadConfig();
        this.debug = this.getConfig().getBoolean("debug.enable", false);
        this.log.setDebug(this.debug);
        this.configInstance = new ConfigInstance(this, this.fileList());
    }

}
