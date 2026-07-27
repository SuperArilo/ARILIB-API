package com.tty.api.dto;

import lombok.Getter;
import org.apache.maven.artifact.versioning.ComparableVersion;

public class PluginVersion {

    @Getter
    private final String currentVersion;
    @Getter
    private final String remoteVersion;

    private final ComparableVersion pCurrentVersion;

    public PluginVersion(String currentVersion, String remoteVersion) {
        this.currentVersion = currentVersion;
        this.remoteVersion = remoteVersion;
        this.pCurrentVersion = new ComparableVersion(currentVersion);
    }

    public boolean hasNewVersion() {
        return this.pCurrentVersion.compareTo(new ComparableVersion(remoteVersion)) < 0;
    }

}
