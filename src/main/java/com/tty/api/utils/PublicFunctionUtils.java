package com.tty.api.utils;

import org.bukkit.Material;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PublicFunctionUtils {

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

}
