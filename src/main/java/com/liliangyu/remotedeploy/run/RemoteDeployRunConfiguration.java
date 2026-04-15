package com.liliangyu.remotedeploy.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Run configuration data model for a remote deployment invocation.
 */
public final class RemoteDeployRunConfiguration extends RunConfigurationBase<RemoteDeployRunConfiguration> {
    private static final String FIELD_SERVER_ID = "serverId";
    private static final String FIELD_LOCAL_PATH = "localPath";
    private static final String FIELD_REMOTE_DIRECTORY = "remoteDirectory";
    private static final String FIELD_COMMAND = "command";
    private static final String FIELD_AFTER_TERMINAL_COMMAND = "afterTerminalCommand";

    private String serverId = "";
    private String localPath = "";
    private String remoteDirectory = "";
    private String command = "";
    private String afterTerminalCommand = "";

    public RemoteDeployRunConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<RemoteDeployRunConfiguration> getConfigurationEditor() {
        return new RemoteDeploySettingsEditor(getProject());
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        return new RemoteDeployRunProfileState(environment, this);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationError {
        if (serverId == null || serverId.isBlank()) {
            throw new RuntimeConfigurationError("Server is required.");
        }

        RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();
        ServerConfig server = settingsService.findServer(serverId).orElse(null);
        if (server == null) {
            throw new RuntimeConfigurationError("Selected server no longer exists.");
        }

        String localPathValue = localPath == null ? "" : localPath.trim();
        if (localPathValue.isEmpty()) {
            throw new RuntimeConfigurationError("Local path is required.");
        }
        if (!Files.exists(Path.of(localPathValue))) {
            throw new RuntimeConfigurationError("Local path does not exist: " + localPathValue);
        }

        String remoteValue = remoteDirectory == null ? "" : remoteDirectory.trim();
        if (remoteValue.isEmpty() && (server.getRemoteDirectory() == null || server.getRemoteDirectory().isBlank())) {
            throw new RuntimeConfigurationError("Remote directory is required.");
        }
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        serverId = JDOMExternalizerUtil.readField(element, FIELD_SERVER_ID, "");
        localPath = JDOMExternalizerUtil.readField(element, FIELD_LOCAL_PATH, "");
        remoteDirectory = JDOMExternalizerUtil.readField(element, FIELD_REMOTE_DIRECTORY, "");
        command = JDOMExternalizerUtil.readField(element, FIELD_COMMAND, "");
        afterTerminalCommand = JDOMExternalizerUtil.readField(element, FIELD_AFTER_TERMINAL_COMMAND, "");
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, FIELD_SERVER_ID, serverId == null ? "" : serverId);
        JDOMExternalizerUtil.writeField(element, FIELD_LOCAL_PATH, localPath == null ? "" : localPath);
        JDOMExternalizerUtil.writeField(element, FIELD_REMOTE_DIRECTORY, remoteDirectory == null ? "" : remoteDirectory);
        JDOMExternalizerUtil.writeField(element, FIELD_COMMAND, command == null ? "" : command);
        JDOMExternalizerUtil.writeField(
            element,
            FIELD_AFTER_TERMINAL_COMMAND,
            afterTerminalCommand == null ? "" : afterTerminalCommand
        );
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId == null ? "" : serverId;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath == null ? "" : localPath;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public void setRemoteDirectory(String remoteDirectory) {
        this.remoteDirectory = remoteDirectory == null ? "" : remoteDirectory;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command == null ? "" : command;
    }

    public String getAfterTerminalCommand() {
        return afterTerminalCommand;
    }

    public void setAfterTerminalCommand(String afterTerminalCommand) {
        this.afterTerminalCommand = afterTerminalCommand == null ? "" : afterTerminalCommand;
    }
}
