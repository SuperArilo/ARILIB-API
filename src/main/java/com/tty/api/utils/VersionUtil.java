package com.tty.api.utils;

import org.bukkit.Bukkit;

public final class VersionUtil {

    /**
     * 判断当前服务器版本是否低于指定版本
     * @param targetVersion "指定是否低于这个版本"
     * @return true 如果当前服务器版本 < targetVersion
     */
    public static boolean isServerVersionLowerThan(String targetVersion) {
        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        String currentVersion = bukkitVersion.split("-")[0];
        return compareVersions(currentVersion, targetVersion) < 0;
    }

    /**
     * 比较两个版本号
     * @return false 小于，true 大于等于
     */
    private static int compareVersions(String v1, String v2) {

        String[] a1 = v1.split("\\.");
        String[] a2 = v2.split("\\.");
        int length = Math.max(a1.length, a2.length);
        for (int i = 0; i < length; i++) {
            int n1 = i < a1.length ? Integer.parseInt(a1[i]) : 0;
            int n2 = i < a2.length ? Integer.parseInt(a2[i]) : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }
        return 0;
    }

}
