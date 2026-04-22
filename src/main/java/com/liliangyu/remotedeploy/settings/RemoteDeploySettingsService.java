package com.liliangyu.remotedeploy.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.SecretStorage;
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
    private static final int MAX_HISTORY_SIZE = 12;

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

    /**
     * Persists one server definition and keeps Password Safe aligned with the selected authentication mode.
     */
    public void saveServerWithSecrets(ServerConfig server, @Nullable String password, @Nullable String passphrase) {
        saveServer(server);
        if (server.getAuthType() == AuthType.PASSWORD) {
            SecretStorage.savePassword(server.getId(), password);
            SecretStorage.savePassphrase(server.getId(), null);
            return;
        }
        SecretStorage.savePassword(server.getId(), null);
        SecretStorage.savePassphrase(server.getId(), passphrase);
    }

    /** Removes the persisted target but intentionally leaves secret cleanup to the caller. */
    public void removeServer(String serverId) {
        state.servers.removeIf(server -> server.getId().equals(serverId));
        if (serverId.equals(state.lastServerId)) {
            state.lastServerId = "";
        }
    }

    /**
     * Removes one stored server together with any password or passphrase bound to that server id.
     */
    public void deleteServer(String serverId) {
        removeServer(serverId);
        SecretStorage.deleteServerSecrets(serverId);
    }

    public String getLastServerId() {
        return state.lastServerId == null ? "" : state.lastServerId;
    }

    public void setLastServerId(String serverId) {
        state.lastServerId = serverId == null ? "" : serverId;
    }

    /**
     * Returns deploy command suggestions from recent history plus the current editor value.
     */
    public List<String> getDeployCommandTemplates(String currentValue) {
        return mergeCommandTemplates(null, currentValue, state.deployCommandHistory);
    }

    /**
     * Stores a deploy command in recency order so the editor can expose lightweight reusable templates.
     */
    public void rememberDeployCommand(String command) {
        rememberCommand(state.deployCommandHistory, command);
    }

    private static RemoteDeploySettingsState copyState(RemoteDeploySettingsState source) {
        RemoteDeploySettingsState copy = new RemoteDeploySettingsState();
        if (source != null) {
            copy.lastServerId = source.lastServerId == null ? "" : source.lastServerId;
            for (ServerConfig server : source.servers) {
                copy.servers.add(new ServerConfig(server));
            }
            copy.deployCommandHistory.addAll(source.deployCommandHistory);
        }
        return copy;
    }

    private static List<String> mergeCommandTemplates(String primaryValue, String currentValue, List<String> history) {
        List<String> result = new ArrayList<>();
        addIfAbsent(result, primaryValue);
        addIfAbsent(result, currentValue);
        for (String command : history) {
            addIfAbsent(result, command);
        }
        return result;
    }

    private static void rememberCommand(List<String> history, String command) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.isEmpty()) {
            return;
        }
        history.removeIf(item -> normalized.equals(item));
        history.add(0, normalized);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
    }

    private static void addIfAbsent(List<String> values, String command) {
        String normalized = command == null ? "" : command.trim();
        if (!normalized.isEmpty() && !values.contains(normalized)) {
            values.add(normalized);
        }
    }
}
