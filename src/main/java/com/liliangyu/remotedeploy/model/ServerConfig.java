package com.liliangyu.remotedeploy.model;

/**
 * Stores reusable SSH connection settings without embedding secret values in the XML state file.
 */
public class ServerConfig {
    private String id = "";
    private String name = "";
    private String host = "";
    private int port = 22;
    private String username = "";
    private AuthType authType = AuthType.PASSWORD;
    private String privateKeyPath = "";

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

    @Override
    public String toString() {
        return name == null || name.isBlank() ? host : name;
    }
}
