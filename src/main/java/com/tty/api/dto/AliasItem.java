package com.tty.api.dto;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class AliasItem {
    @Expose
    @SerializedName("enable")
    private boolean enable;
    @Expose
    @SerializedName("usage")
    private String usage;
}
