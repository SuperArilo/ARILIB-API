package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import lombok.Data;

import java.util.List;

@Data
public class DataItems {
    @Expose
    private List<Integer> slot;
    @Expose
    private List<String> lore;
}
