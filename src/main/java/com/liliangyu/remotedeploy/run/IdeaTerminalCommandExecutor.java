package com.liliangyu.remotedeploy.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.ui.TerminalWidget;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.ServerConfig;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the IDE Terminal and starts an ssh session so post-deploy log viewing stays inside IDEA.
 */
final class IdeaTerminalCommandExecutor {
    private final Project project;

    IdeaTerminalCommandExecutor(Project project) {
        this.project = project;
    }

    /**
     * Creates a fresh terminal tab that connects to the selected server and immediately runs the requested remote command.
     */
    void openAndExecute(String tabTitle, String localPath, ServerConfig server, String remoteCommand) {
        if (remoteCommand == null || remoteCommand.isBlank()) {
            return;
        }

        List<String> sshCommand = buildSshCommand(server, remoteCommand.trim());
        ApplicationManager.getApplication().invokeAndWait(() -> {
            TerminalToolWindowManager terminalManager = TerminalToolWindowManager.getInstance(project);
            TerminalWidget terminal =
                terminalManager.createNewSession(resolveWorkingDirectory(localPath), tabTitle, sshCommand, true, true);
        });
    }

    /**
     * Uses the selected local file/folder location as terminal cwd when possible, and falls back to the project root.
     */
    private String resolveWorkingDirectory(String localPath) {
        if (localPath != null && !localPath.isBlank()) {
            Path path = Path.of(localPath);
            if (Files.isDirectory(path)) {
                return path.toString();
            }
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return parent.toString();
            }
        }

        String basePath = project.getBasePath();
        if (basePath != null && !basePath.isBlank()) {
            return basePath;
        }
        return System.getProperty("user.home", ".");
    }

    /**
     * Reuses the stored server connection settings so users only need to type the remote follow-up command itself.
     */
    private List<String> buildSshCommand(ServerConfig server, String remoteCommand) {
        List<String> command = new ArrayList<>();
        command.add("ssh");
        command.add("-t");
        command.add("-p");
        command.add(String.valueOf(server.getPort()));

        if (server.getAuthType() == AuthType.PRIVATE_KEY) {
            String keyPath = server.getPrivateKeyPath();
            if (keyPath != null && !keyPath.isBlank()) {
                command.add("-i");
                command.add(keyPath.trim());
            }
        }

        String username = server.getUsername() == null ? "" : server.getUsername().trim();
        String destination = username.isEmpty() ? server.getHost() : username + "@" + server.getHost();
        command.add(destination);
        command.add(remoteCommand);
        return command;
    }
}
