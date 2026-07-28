package com.tty.api.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorConverterLegacy {

    private static final Map<Character, String> LEGACY_MAP = new HashMap<>();
    private static final Pattern BUNGEECORD_RGB = Pattern.compile("§x(§[0-9a-fA-F]){6}");

    static {
        LEGACY_MAP.put('0', "black");
        LEGACY_MAP.put('1', "dark_blue");
        LEGACY_MAP.put('2', "dark_green");
        LEGACY_MAP.put('3', "dark_aqua");
        LEGACY_MAP.put('4', "dark_red");
        LEGACY_MAP.put('5', "dark_purple");
        LEGACY_MAP.put('6', "gold");
        LEGACY_MAP.put('7', "gray");
        LEGACY_MAP.put('8', "dark_gray");
        LEGACY_MAP.put('9', "blue");
        LEGACY_MAP.put('a', "green");
        LEGACY_MAP.put('b', "aqua");
        LEGACY_MAP.put('c', "red");
        LEGACY_MAP.put('d', "light_purple");
        LEGACY_MAP.put('e', "yellow");
        LEGACY_MAP.put('f', "white");
        LEGACY_MAP.put('r', "reset");
        LEGACY_MAP.put('l', "bold");
        LEGACY_MAP.put('o', "italic");
        LEGACY_MAP.put('n', "underlined");
        LEGACY_MAP.put('m', "strikethrough");
        LEGACY_MAP.put('k', "obfuscated");
    }

    public static String convert(String input) {
        if (input == null) return "";
        return convertLegacyCodes(convertBungeeCordRgb(input.replace('&', '§')));
    }

    private static String convertBungeeCordRgb(String input) {
        Matcher matcher = BUNGEECORD_RGB.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group().replaceAll("§x|§", "");
            matcher.appendReplacement(builder, "<#" + hex + ">");
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String convertLegacyCodes(String input) {
        StringBuilder builder = new StringBuilder();
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if ((c == '&' || c == '§') && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                String color = LEGACY_MAP.get(code);
                if (color != null) {
                    builder.append("<").append(color).append(">");
                    i++;
                    continue;
                }
            }
            builder.append(c);
        }
        return builder.toString();
    }

}