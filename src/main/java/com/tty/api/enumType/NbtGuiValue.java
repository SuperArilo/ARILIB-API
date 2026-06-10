package com.tty.api.enumType;

public enum NbtGuiValue implements NbtEnum{
    GUI_MASK_ICON("gui_mask_icon"),
    GUI_FUNCTION_ICON("gui_function_icon"),
    GUI_DATA_ID("gui_data_id");

    private final String key;

    NbtGuiValue(String key) {
        this.key = key;
    }


    @Override
    public String getKey() {
        return this.key;
    }
}
