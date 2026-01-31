package com.tty.api.enumType;

public interface GuiKeyEnum {

    String getType();

    static <E extends Enum<E> & GuiKeyEnum> E fromKey(Class<E> enumClass, String key) {
        for (E e : enumClass.getEnumConstants()) {
            if (e.getType().equals(key)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown key '" + key + "' for enum " + enumClass.getName());
    }

}
