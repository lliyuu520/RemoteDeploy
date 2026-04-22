package com.liliangyu.remotedeploy.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.ServerConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Browses one remote directory at a time so UI callers can lazily navigate the server without fetching the full tree.
 */
public final class RemoteDirectoryService {
    private final SshConnectionService sshConnectionService = new SshConnectionService();

    /**
     * Resolves the requested directory, falls back to the remote home when needed, and returns only child directories for navigation.
     */
    public DirectorySnapshot loadDirectory(ServerConfig server, String requestedDirectory, ProgressIndicator indicator) throws IOException {
        try (SSHClient client = sshConnectionService.openClient(server, indicator, null, null);
             SFTPClient sftpClient = client.newSFTPClient()) {
            String currentDirectory = resolveDirectory(sftpClient, requestedDirectory);
            indicator.checkCanceled();
            indicator.setText(RemoteDeployBundle.message("service.progress.loadingRemoteDirectory", currentDirectory));

            List<DirectoryEntry> directories = sftpClient.ls(currentDirectory).stream()
                .filter(RemoteResourceInfo::isDirectory)
                .map(info -> new DirectoryEntry(info.getName(), RemotePathSupport.join(currentDirectory, info.getName())))
                .filter(entry -> !".".equals(entry.name()) && !"..".equals(entry.name()))
                .sorted(Comparator.comparing(DirectoryEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
            return new DirectorySnapshot(currentDirectory, directories);
        }
    }

    /**
     * Keeps directory browsing resilient by starting from the remote home when the typed path is blank, stale, or points to a file.
     */
    private String resolveDirectory(SFTPClient sftpClient, String requestedDirectory) throws IOException {
        String fallbackDirectory = sftpClient.canonicalize(".");
        String normalized = RemotePathSupport.normalizeOptionalDirectory(requestedDirectory);
        if (normalized.isBlank()) {
            return fallbackDirectory;
        }

        var attributes = sftpClient.statExistence(normalized);
        if (attributes == null || attributes.getType() != FileMode.Type.DIRECTORY) {
            return fallbackDirectory;
        }
        return sftpClient.canonicalize(normalized);
    }

    public record DirectorySnapshot(String currentDirectory, List<DirectoryEntry> directories) {
    }

    public record DirectoryEntry(String name, String path) {
    }
}
