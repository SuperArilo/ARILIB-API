package com.tty.api.enumType;

import lombok.Getter;

@Getter
public enum FunctionType {

    BACK("back"),
    REBACK("reback"),
    ICON("icon"),
    MASK_ICON("mask_icon"),
    LOCATION("location"),
    TOP_SLOT("top_slot"),
    DATA("data"),
    RENAME("rename"),
    SAVE("save"),
    DELETE("delete"),
    CANCEL("cancel"),
    CONFIRM("confirm"),
    PREV_PAGE("prev_page"),
    NEXT_PAGE("next_page"),
    PAGE_DISABLE("page_disable"),
    PERMISSION("permission"),
    COST("cost");

    private final String name;

    FunctionType(String name) {
        this.name = name;
    }

}
