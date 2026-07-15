package com.tty.api.dto;

import lombok.Data;
import org.apache.maven.artifact.versioning.ComparableVersion;

@Data
public class PluginVersion {

    private String currentVersion = "";
    private String remoteVersion = "";

    public boolean hasNewVersion() {
        return new ComparableVersion(currentVersion).compareTo(new ComparableVersion(remoteVersion)) < 0;
    }

}
