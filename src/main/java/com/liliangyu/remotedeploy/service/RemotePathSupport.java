package com.liliangyu.remotedeploy.service;

import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;

import java.io.IOException;

/**
 * Normalizes remote paths in one place so upload, command execution, and directory browsing interpret separators consistently.
 */
public final class RemotePathSupport {
    private RemotePathSupport() {
    }

    /**
     * Requires a non-empty remote directory because deploy flows cannot proceed without a target location.
     */
    public static String requireDirectory(String remoteDirectory) throws IOException {
        String normalized = normalizeOptionalDirectory(remoteDirectory);
        if (normalized.isBlank()) {
            throw new IOException(RemoteDeployBundle.message("service.validation.remoteDirectoryRequired"));
        }
        return normalized;
    }

    /**
     * Keeps user-entered remote paths predictable by trimming whitespace, converting Windows separators, and collapsing trailing slashes.
     */
    public static String normalizeOptionalDirectory(String remoteDirectory) {
        String normalized = remoteDirectory == null ? "" : remoteDirectory.trim().replace('\\', '/');
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Joins one child segment onto a remote directory while preserving the POSIX root path.
     */
    public static String join(String parent, String child) {
        return "/".equals(parent) ? "/" + child : parent + "/" + child;
    }

    /**
     * Resolves the parent directory for navigation without ever moving above the logical root.
     */
    public static String parentOf(String directory) {
        String normalized = normalizeOptionalDirectory(directory);
        if (normalized.isBlank() || "/".equals(normalized)) {
            return "/";
        }

        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : normalized.substring(0, lastSlash);
    }
}
