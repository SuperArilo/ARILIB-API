package com.tty.api.configuration;

import com.tty.api.AbstractJavaPlugin;

public abstract class AllowDownloadConfiguration extends BaseConfiguration implements AllowVersionConfiguration {

    public AllowDownloadConfiguration(AbstractJavaPlugin plugin, String relativePath) {
        super(plugin, relativePath);
    }

    public AllowDownloadConfiguration(AbstractJavaPlugin plugin) {
        super(plugin);
    }

    public abstract String  getDownloadUrl();

    @Override
    public double getVersion() {
        return this.getDouble("version", 0.0);
    }

}
