package com.tty.api.configuration;

import com.tty.api.AbstractJavaPlugin;

public abstract class LangConfiguration extends AllowDownloadConfiguration {

    public LangConfiguration(AbstractJavaPlugin plugin, String path) {
        super(plugin, path.replace("[lang]", plugin.getConfig().getString("lang", "cn")));
    }

    public LangConfiguration(AbstractJavaPlugin plugin) {
        super(plugin);
    }

}
