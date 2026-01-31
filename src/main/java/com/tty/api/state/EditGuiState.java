package com.tty.api.state;

import com.tty.api.enumType.FunctionType;
import com.tty.api.gui.BaseInventory;
import lombok.Getter;
import org.bukkit.entity.Entity;

public class EditGuiState extends State {

    @Getter
    private final BaseInventory i;
    @Getter
    private final FunctionType functionType;

    public EditGuiState(Entity owner, int count, BaseInventory i, FunctionType functionType) {
        super(owner, count);
        this.i = i;
        this.functionType = functionType;
    }

}
