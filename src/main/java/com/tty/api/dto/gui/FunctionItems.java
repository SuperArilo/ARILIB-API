package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import com.tty.api.enumType.FunctionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FunctionItems extends BaseItem {
    @Expose
    private FunctionType type;
}
