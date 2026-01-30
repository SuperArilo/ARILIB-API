package com.tty.api.dto.gui;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class BaseMenu implements Serializable {
    private String title;
    private Integer row;
    private Mask mask;
    private Map<String, FunctionItems> functionItems;
}
