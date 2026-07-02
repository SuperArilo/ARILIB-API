package com.tty.api.configuration;

import com.tty.api.AbstractJavaPlugin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class AllowDownloadConfiguration extends BaseConfiguration implements AllowVersionConfiguration {

    private static final String DOWNLOAD_URL = "https://raw.githubusercontent.com/SuperArilo/Plugin-Configs/main/";
    private final AbstractJavaPlugin plugin;

    public AllowDownloadConfiguration(AbstractJavaPlugin plugin, String path) {
        super(plugin, path);
        this.plugin = plugin;
    }

    public String getUrl() {
        return DOWNLOAD_URL + this.plugin.getName() + "/" + Arrays.stream(this.getPath().split("/")).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).collect(Collectors.joining("/"));
    }


    @Override
    public double getVersion() {
        return this.getValue("version", Double.class, 0);
    }
}
