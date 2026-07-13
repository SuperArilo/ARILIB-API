package com.tty.api.dto;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class BaseVersion {

    @Expose
    @SerializedName("version")
    private double version;

}
