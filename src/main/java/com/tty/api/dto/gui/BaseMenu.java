package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import com.tty.api.dto.BaseVersion;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseMenu extends BaseVersion implements Serializable {
    @Expose
    private String title;
    @Expose
    private Integer row;
    @Expose
    private Mask mask;
    @Expose
    private Map<String, FunctionItems> functionItems;
}
