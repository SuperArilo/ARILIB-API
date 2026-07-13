package com.tty.api.dto;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class CommandAlias extends BaseVersion {

    @Expose
    @SerializedName("aliases")
    private Map<String, AliasItem> aliases;

}
