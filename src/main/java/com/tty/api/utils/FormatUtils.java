package com.tty.api.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.logging.Level;

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
            throw new IllegalArgumentException("location string is null or empty!");
        }

        if (locString.startsWith("Location{") && locString.endsWith("}")) {
            locString = locString.substring(9, locString.length() - 1);
        }

        String instance = null;
        double x = 0, y = 0, z = 0;
        float pitch = 0, yaw = 0;

        int length = locString.length();
        int i = 0;

        while (i < length) {
            int eqIdx = locString.indexOf('=', i);
            if (eqIdx < 0) break;

            String key = locString.substring(i, eqIdx).trim();
            int valueEnd = locString.indexOf(',', eqIdx + 1);
            if (valueEnd < 0) valueEnd = length;
            String value = locString.substring(eqIdx + 1, valueEnd).trim();

            switch (key) {
                case "world": {
                    if (value.startsWith("CraftWorld{") && value.endsWith("}")) {
                        String inner = value.substring(11, value.length() - 1);
                        int keyIndex = inner.indexOf("key=");
                        int nameIndex = inner.indexOf("name=");
                        int startIndex = -1;
                        if (keyIndex >= 0) startIndex = keyIndex + 4;
                        else if (nameIndex >= 0) startIndex = nameIndex + 5;
                        if (startIndex >= 0) {
                            int endIdx = inner.indexOf(',', startIndex);
                            if (endIdx < 0) endIdx = inner.indexOf('}', startIndex);
                            if (endIdx < 0) endIdx = inner.length();
                            instance = inner.substring(startIndex, endIdx).trim();
                        } else {
                            instance = value;
                        }
                    } else {
                        instance = value;
                    }
                    break;
                }
                case "x": {
                    try {
                        x = Double.parseDouble(value);
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid x: " + value, e);
                    }
                    break;
                }
                case "y": {
                    try {
                        y = Double.parseDouble(value);
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid y: " + value, e);
                    }
                    break;
                }
                case "z": {
                    try {
                        z = Double.parseDouble(value);
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid z: " + value, e);
                    }
                    break;
                }
                case "pitch": {
                    try {
                        pitch = Float.parseFloat(value);
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid pitch: " + value, e);
                    }
                    break;
                }
                case "yaw": {
                    try {
                        yaw = Float.parseFloat(value);
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid yaw: " + value, e);
                    }
                    break;
                }
            }
            i = valueEnd + 1;
        }

        if (instance == null) {
            try {
                Location location = Location.deserialize(new Gson().fromJson(locString, new TypeToken<Map<String, Object>>() {}.getType()));
                if (location.getWorld() == null) throw new IllegalArgumentException();
            } catch (Exception e) {
                Bukkit.getServer().getLogger().log(Level.WARNING, "world field missing", e);
                return new Location(Bukkit.getWorlds().getFirst(), 0, 0, 0);
            }
        }

        World world = null;
        if (instance.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(instance);
            if (key != null) {
                world = Bukkit.getServer().getWorld(key);
            }
        }
        if (world == null) {
            world = Bukkit.getServer().getWorld(instance);
        }
        if (world == null) {
            throw new IllegalArgumentException("world not found: " + instance);
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

}
