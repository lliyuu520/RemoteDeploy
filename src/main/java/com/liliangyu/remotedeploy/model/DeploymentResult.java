package com.liliangyu.remotedeploy.model;

import java.util.List;

/** Keeps the observable deployment outcome so the UI can show a useful summary and command output. */
public record DeploymentResult(List<String> uploadedPaths, String stdout, String stderr, Integer exitCode) {
    public DeploymentResult {
        uploadedPaths = List.copyOf(uploadedPaths);
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    public boolean commandSucceeded() {
        return exitCode == null || exitCode == 0;
    }

    public boolean hasOutput() {
        return !stdout.isBlank() || !stderr.isBlank() || exitCode != null;
    }
}
