package com.tty.api.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.text.DecimalFormat;

public class FormatUtils {

    /**
     * 格式化数字保留两位小数
     * @param number 需要格式化的数字（支持所有Number子类）
     * @return 格式化后的字符串，输入为null时返回"0.00"
     */
    public static String formatTwoDecimalPlaces(Number number) {
        if (number == null) return "0.00";
        return ThreadLocal.withInitial(() -> new DecimalFormat("0.00")).get().format(number);
    }

    /**
     * 将 Component 转成 String
     * @param component 被转对象
     * @return 返回String
     */
    public static String componentToString(Component component) {
        if (component == null) return "";
        if(component instanceof TextComponent) {
            return ((TextComponent) component).content();
        }
        return component.toString();
    }

    /**
     * 返回 基础格式化的文本坐标
     * @param x x轴
     * @param y y轴
     * @param z z轴
     * @return 返回基础格式化的文本坐标
     */
    public static String XYZText(Double x, Double y, Double z) {
        return "&2x: &6" + FormatUtils.formatTwoDecimalPlaces(x) +
                " &2y: &6" + FormatUtils.formatTwoDecimalPlaces(y) +
                " &2z: &6" + FormatUtils.formatTwoDecimalPlaces(z);
    }

    /**
     * 将 string 的 Location 转换成 Location对象
     * @param locString string
     * @return Location 对象
     */
    public static Location parseLocation(String locString) {
        if (locString == null || locString.isEmpty()) {
            throw new IllegalArgumentException("Location string is null or empty!");
        }

        // 去掉 Location{...} 外层
        if (locString.startsWith("Location{") && locString.endsWith("}")) {
            locString = locString.substring(9, locString.length() - 1);
        }

        String worldName = null;
        double x = 0, y = 0, z = 0;
        float pitch = 0, yaw = 0;

        int len = locString.length();
        int i = 0;

        while (i < len) {
            // 找 key
            int eqIdx = locString.indexOf('=', i);
            if (eqIdx < 0) break;

            String key = locString.substring(i, eqIdx).trim();

            // 找 value 结束位置
            int valEnd = locString.indexOf(',', eqIdx + 1);
            if (valEnd < 0) valEnd = len;

            String value = locString.substring(eqIdx + 1, valEnd).trim();

            switch (key) {
                case "world":
                    // 支持 CraftWorld{name=world}
                    if (value.startsWith("CraftWorld")) {
                        int nameIdx = value.indexOf("name=");
                        if (nameIdx >= 0) {
                            int nameStart = nameIdx + 5;
                            int nameEnd = value.indexOf('}', nameStart);
                            if (nameEnd < 0) nameEnd = value.length();
                            worldName = value.substring(nameStart, nameEnd);
                        } else {
                            worldName = value;
                        }
                    } else {
                        worldName = value;
                    }
                    break;
                case "x": x = Double.parseDouble(value); break;
                case "y": y = Double.parseDouble(value); break;
                case "z": z = Double.parseDouble(value); break;
                case "pitch": pitch = Float.parseFloat(value); break;
                case "yaw": yaw = Float.parseFloat(value); break;
            }

            i = valEnd + 1;
        }

        if (worldName == null) {
            throw new IllegalArgumentException("World not found in location string: " + locString);
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World not found on server: " + worldName);
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

}
