package com.liliangyu.remotedeploy.model;

/**
 * Stores a reusable deployment target without embedding its secret values in the XML state file.
 */
public class ServerConfig {
    private String id = "";
    private String name = "";
    private String host = "";
    private int port = 22;
    private String username = "";
    private AuthType authType = AuthType.PASSWORD;
    private String privateKeyPath = "";
    private String remoteDirectory = "";
    private String deployCommand = "";

    public ServerConfig() {
    }

    public ServerConfig(ServerConfig other) {
        this.id = other.id;
        this.name = other.name;
        this.host = other.host;
        this.port = other.port;
        this.username = other.username;
        this.authType = other.authType;
        this.privateKeyPath = other.privateKeyPath;
        this.remoteDirectory = other.remoteDirectory;
        this.deployCommand = other.deployCommand;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public void setRemoteDirectory(String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
    }

    public String getDeployCommand() {
        return deployCommand;
    }

    public void setDeployCommand(String deployCommand) {
        this.deployCommand = deployCommand;
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? host : name;
    }
}
