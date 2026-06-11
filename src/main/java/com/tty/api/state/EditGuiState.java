package com.tty.api.state;

import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiKeyEnum;
import lombok.Getter;
import org.bukkit.entity.Entity;

public class EditGuiState<T> extends State {

    @Getter
    private final T data;
    @Getter
    private final FunctionType functionType;
    @Getter
    private final GuiKeyEnum type;

    public EditGuiState(Entity owner, int count, T data, FunctionType functionType, GuiKeyEnum type) {
        super(owner, count);
        this.data = data;
        this.functionType = functionType;
        this.type = type;
    }

}
