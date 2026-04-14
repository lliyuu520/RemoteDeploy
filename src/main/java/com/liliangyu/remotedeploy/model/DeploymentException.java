package com.liliangyu.remotedeploy.model;

import java.io.IOException;

/**
 * Wraps a command failure while preserving the upload result and captured command output for the UI.
 */
public class DeploymentException extends IOException {
    private final DeploymentResult result;

    public DeploymentException(String message, DeploymentResult result) {
        super(message);
        this.result = result;
    }

    public DeploymentResult getResult() {
        return result;
    }
}
