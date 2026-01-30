package com.tty.api.dto.gui;

import com.tty.api.enumType.FunctionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FunctionItems extends BaseItem {
    private FunctionType type;
}
