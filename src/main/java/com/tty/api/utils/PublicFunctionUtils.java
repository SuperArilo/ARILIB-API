package com.tty.api.utils;

import org.bukkit.Material;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PublicFunctionUtils {

    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]*$");

    /**
     * 检查材质是否是ITEM
     * @param material 被检查的材质
     * @return 返回一个正确的材质
     */
    public static Material checkIsItem(Material material) {
        if(!material.isItem() || !material.isSolid()) {
            return Material.DIRT;
        }
        return material;
    }

    /**
     * 随机得到指定范围内的随机数
     * @param min 最小值
     * @param max 最大值
     * @return 随机数
     */
    public static int randomGenerator(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("The maximum value must be greater than the minimum value");
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 根据输入的字符串来匹配和返回对应的列表
     * @param input 输入
     * @param raw 需要匹配的列表
     * @return 返回的匹配列表
     */
    public static Set<String> tabList(String input, Set<String> raw) {
        if (input == null) input = "";
        String finalInput = input;
        return raw.stream().filter(s -> s.startsWith(finalInput)).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 检查创建实体的 id 字符串是否合法
     * @param id 待检查字符串
     * @return 空值或不符合格式返回 false
     */
    public static boolean isEntityIdValid(String id) {
        return id != null && ENTITY_ID_PATTERN.matcher(id).matches();
    }

}
