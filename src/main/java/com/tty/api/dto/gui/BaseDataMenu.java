package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseDataMenu extends BaseMenu {
    @Expose
    private DataItems dataItems;
    @Expose
    private PageDisable pageDisable;
}
