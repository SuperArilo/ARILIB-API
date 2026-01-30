package com.tty.api.dto.gui;

import com.tty.api.enumType.FunctionType;
import lombok.Data;

@Data
public class PageDisable {
    private String name;
    private String material;
    private FunctionType type;
}
