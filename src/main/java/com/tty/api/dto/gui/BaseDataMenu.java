package com.tty.api.dto.gui;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseDataMenu extends BaseMenu {
    private DataItems dataItems;
    private PageDisable pageDisable;
}
