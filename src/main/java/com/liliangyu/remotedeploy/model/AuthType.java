package com.liliangyu.remotedeploy.model;

import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;

/** Supported authentication modes for an SSH deployment target. */
public enum AuthType {
    PASSWORD("auth.password"),
    PRIVATE_KEY("auth.privateKey");

    private final String messageKey;

    AuthType(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        return RemoteDeployBundle.message(messageKey);
    }
}
