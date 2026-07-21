package com.tty.api.service;


import de.tr7zw.nbtapi.iface.NBTFileHandle;

import org.jetbrains.annotations.Nullable;

public interface NBTDataService {
    @Nullable NBTFileHandle getData(String playerUUID);
}
