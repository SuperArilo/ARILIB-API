package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import com.tty.api.enumType.FunctionType;
import lombok.Data;

@Data
public class PageDisable {
    @Expose
    private String name;
    @Expose
    private String material;
    @Expose
    private FunctionType type;
}
