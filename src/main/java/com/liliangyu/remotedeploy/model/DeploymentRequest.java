package com.liliangyu.remotedeploy.model;

/** Captures one explicit deploy run after the dialog resolves all user inputs. */
public record DeploymentRequest(ServerConfig server, String localPath, String remoteDirectory, String command) {
    public DeploymentRequest {
        server = new ServerConfig(server);
        localPath = localPath == null ? "" : localPath.trim();
        remoteDirectory = remoteDirectory == null ? "" : remoteDirectory.trim();
        command = command == null ? "" : command.trim();
    }
}
