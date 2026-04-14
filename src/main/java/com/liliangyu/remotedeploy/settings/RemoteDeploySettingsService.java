package com.liliangyu.remotedeploy.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.liliangyu.remotedeploy.model.ServerConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Centralizes CRUD operations for deployment targets so dialogs work with copies instead of live state.
 */
@Service(Service.Level.APP)
@State(name = "RemoteDeploySettings", storages = @Storage("remoteDeploy.xml"))
public final class RemoteDeploySettingsService implements PersistentStateComponent<RemoteDeploySettingsState> {
    private RemoteDeploySettingsState state = new RemoteDeploySettingsState();

    public static RemoteDeploySettingsService getInstance() {
        return ApplicationManager.getApplication().getService(RemoteDeploySettingsService.class);
    }

    @Override
    public @Nullable RemoteDeploySettingsState getState() {
        return copyState(state);
    }

    @Override
    public void loadState(@NotNull RemoteDeploySettingsState loadedState) {
        state = copyState(loadedState);
    }

    /** Returns detached copies so UI edits are only committed through saveServer(). */
    public List<ServerConfig> getServers() {
        List<ServerConfig> copy = new ArrayList<>();
        for (ServerConfig server : state.servers) {
            copy.add(new ServerConfig(server));
        }
        return copy;
    }

    /** Finds a single server by id using the same detached copy approach as getServers(). */
    public Optional<ServerConfig> findServer(String serverId) {
        return getServers().stream().filter(server -> server.getId().equals(serverId)).findFirst();
    }

    /** Inserts a new server or replaces an existing one with the same id. */
    public void saveServer(ServerConfig server) {
        ServerConfig detached = new ServerConfig(server);
        for (int i = 0; i < state.servers.size(); i++) {
            if (state.servers.get(i).getId().equals(detached.getId())) {
                state.servers.set(i, detached);
                return;
            }
        }
        state.servers.add(detached);
    }

    /** Removes the persisted target but intentionally leaves secret cleanup to the caller. */
    public void removeServer(String serverId) {
        state.servers.removeIf(server -> server.getId().equals(serverId));
        if (serverId.equals(state.lastServerId)) {
            state.lastServerId = "";
        }
    }

    public String getLastServerId() {
        return state.lastServerId == null ? "" : state.lastServerId;
    }

    public void setLastServerId(String serverId) {
        state.lastServerId = serverId == null ? "" : serverId;
    }

    private static RemoteDeploySettingsState copyState(RemoteDeploySettingsState source) {
        RemoteDeploySettingsState copy = new RemoteDeploySettingsState();
        if (source != null) {
            copy.lastServerId = source.lastServerId == null ? "" : source.lastServerId;
            for (ServerConfig server : source.servers) {
                copy.servers.add(new ServerConfig(server));
            }
        }
        return copy;
    }
}
