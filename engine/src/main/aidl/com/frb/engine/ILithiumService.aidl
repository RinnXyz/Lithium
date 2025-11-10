// ILithiumService.aidl
package com.rinn.engine;

import com.rinn.engine.IFileService;
import com.rinn.engine.IRuntimeService;
import com.rinn.engine.ILithiumApplication;
import moe.shizuku.server.IShizukuService;
import rikka.parcelablelist.ParcelableListSlice;
import com.rinn.engine.data.LithiumInfo;
import com.rinn.engine.data.PluginInfo;

parcelable Environment;

interface ILithiumService {
    IFileService getFileService() = 2;
    IRuntimeService getRuntimeService(in String[] command, in Environment env, in String dir) = 3;
    LithiumInfo getInfo() = 4;
    void bindLithiumApplication(in ILithiumApplication app) = 5;
    ParcelableListSlice<PackageInfo> getPackages(int flags) = 6;
    ParcelableListSlice<PluginInfo> getPlugins() = 7;
    PluginInfo getPluginById(in String id) = 8;
    boolean isFirstInit(boolean markAsFirstInit) = 9;
    IShizukuService getShizukuService() = 10;
    void enableShizukuService(boolean enable) = 11;
    Environment getEnvironment(int envType) = 12;
    void setNewEnvironment(in Environment env) = 13;
    void destroy() = 16777114;
}