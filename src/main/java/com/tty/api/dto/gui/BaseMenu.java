package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class BaseMenu implements Serializable {
    @Expose
    private String title;
    @Expose
    private Integer row;
    @Expose
    private Mask mask;
    @Expose
    private Map<String, FunctionItems> functionItems;
}
