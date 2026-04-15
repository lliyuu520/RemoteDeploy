package com.liliangyu.remotedeploy.settings;

import com.liliangyu.remotedeploy.model.ServerConfig;

import java.util.ArrayList;
import java.util.List;

/** Serializable application state for stored deployment targets. */
public class RemoteDeploySettingsState {
    public List<ServerConfig> servers = new ArrayList<>();
    public String lastServerId = "";
    public List<String> deployCommandHistory = new ArrayList<>();
    public List<String> afterRemoteCommandHistory = new ArrayList<>();
}
