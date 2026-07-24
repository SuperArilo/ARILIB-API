package com.tty.api.enumType;

public interface PlaceholderTypeEnum {
    String getType();

    static String testBuild(PlaceholderTypeEnum typeEnum) {
        return "<" + typeEnum.getType() + ">";
    }

    static String testBuild(String content) {
        return "<" + content + ">";
    }

}
