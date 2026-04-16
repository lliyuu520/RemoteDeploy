package com.liliangyu.remotedeploy.settings;

import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.i18n.UiLanguage;

import java.util.ArrayList;
import java.util.List;

/** Serializable application state for stored deployment targets and lightweight UI preferences. */
public class RemoteDeploySettingsState {
    public List<ServerConfig> servers = new ArrayList<>();
    public String lastServerId = "";
    public List<String> deployCommandHistory = new ArrayList<>();
    public String uiLanguage = UiLanguage.ENGLISH.id();
}
