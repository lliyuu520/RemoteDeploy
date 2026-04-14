package com.liliangyu.remotedeploy.model;

/** Supported authentication modes for an SSH deployment target. */
public enum AuthType {
    PASSWORD("Password"),
    PRIVATE_KEY("Private Key");

    private final String displayName;

    AuthType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
