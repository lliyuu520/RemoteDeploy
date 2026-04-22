package com.liliangyu.remotedeploy.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.DeploymentException;
import com.liliangyu.remotedeploy.model.DeploymentRequest;
import com.liliangyu.remotedeploy.model.DeploymentResult;
import com.liliangyu.remotedeploy.model.ServerConfig;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.SSHClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs the full deployment workflow: connect, authenticate, upload, then optionally execute a remote command.
 */
public final class SshDeployService {
    private final SshConnectionService sshConnectionService = new SshConnectionService();

    /**
     * Executes the minimum viable deployment flow against a single SSH target.
     */
    public DeploymentResult deploy(DeploymentRequest request, ProgressIndicator indicator) throws IOException {
        Path localPath = Path.of(request.localPath());
        if (!Files.exists(localPath)) {
            throw new IOException(RemoteDeployBundle.message("service.validation.localPathMissing", localPath));
        }

        ServerConfig server = request.server();
        String remoteDirectory = RemotePathSupport.requireDirectory(request.remoteDirectory());
        List<String> uploadedPaths = new ArrayList<>();

        try (SSHClient client = sshConnectionService.openClient(server, indicator, null, null)) {
            try (SFTPClient sftpClient = client.newSFTPClient()) {
                uploadLocalPath(localPath, remoteDirectory, request, sftpClient, indicator, uploadedPaths);
            }

            if (request.command().isBlank()) {
                return new DeploymentResult(uploadedPaths, "", "", null);
            }

            indicator.setText(RemoteDeployBundle.message("service.progress.runningRemoteCommand"));
            CommandResult commandResult = executeCommand(
                client,
                RemoteCommandSupport.buildExecCommand(remoteDirectory, request.command())
            );
            DeploymentResult result = new DeploymentResult(uploadedPaths, commandResult.stdout(), commandResult.stderr(), commandResult.exitCode());
            if (!result.commandSucceeded()) {
                throw new DeploymentException(RemoteDeployBundle.message("service.command.exitStatus", result.exitCode()), result);
            }
            return result;
        }
    }

    /**
     * Verifies the current server form values can open and authenticate an SSH session before the dialog is saved.
     */
    public void testConnection(ServerConfig server, String password, String passphrase, ProgressIndicator indicator) throws IOException {
        try (SSHClient client = sshConnectionService.openClient(server, indicator, password, passphrase)) {
            indicator.setText(RemoteDeployBundle.message("service.progress.connectionSuccessful"));
        }
    }

    /**
     * Treats the remote input as a target directory: files land directly inside it and folders keep their top-level name.
     */
    private void uploadLocalPath(Path localPath, String remoteDirectory, DeploymentRequest request, SFTPClient sftpClient, ProgressIndicator indicator,
                                 List<String> uploadedPaths) throws IOException {
        sftpClient.mkdirs(remoteDirectory);

        if (Files.isRegularFile(localPath)) {
            indicator.checkCanceled();
            String remoteFile = RemotePathSupport.join(remoteDirectory, resolveRemoteFileName(localPath.getFileName().toString(), request));
            indicator.setText(RemoteDeployBundle.message("service.progress.uploadingRoot", localPath.getFileName()));
            sftpClient.put(localPath.toString(), remoteFile);
            indicator.setFraction(1.0d);
            uploadedPaths.add(remoteFile);
            return;
        }

        String remoteRoot = RemotePathSupport.join(remoteDirectory, localPath.getFileName().toString());
        sftpClient.mkdirs(remoteRoot);

        long totalFiles = Files.walk(localPath).filter(Files::isRegularFile).count();
        long uploadedCount = 0;

        try (var paths = Files.walk(localPath)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                indicator.checkCanceled();
                Path relativePath = localPath.relativize(path);
                String remotePath = relativePath.getNameCount() == 0
                    ? remoteRoot
                    : RemotePathSupport.join(remoteRoot, relativePath.toString().replace('\\', '/'));

                if (Files.isDirectory(path)) {
                    sftpClient.mkdirs(remotePath);
                    continue;
                }

                indicator.setText(RemoteDeployBundle.message("service.progress.uploadingRelative", relativePath));
                sftpClient.put(path.toString(), remotePath);
                uploadedPaths.add(remotePath);
                uploadedCount++;
                if (totalFiles > 0) {
                    indicator.setFraction((double) uploadedCount / totalFiles);
                }
            }
        }
    }

    /**
     * Reads stdout and stderr concurrently so verbose deployment scripts do not block on a full SSH buffer.
     */
    private CommandResult executeCommand(SSHClient client, String commandText) throws IOException {
        try (Session session = client.startSession(); ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Session.Command command = session.exec(commandText);
            Future<String> stdout = executor.submit(() -> IOUtils.readFully(command.getInputStream()).toString(StandardCharsets.UTF_8));
            Future<String> stderr = executor.submit(() -> IOUtils.readFully(command.getErrorStream()).toString(StandardCharsets.UTF_8));
            command.join();
            Integer exitCode = command.getExitStatus();
            return new CommandResult(resolveFuture(stdout), resolveFuture(stderr), exitCode == null ? -1 : exitCode);
        }
    }

    private String resolveFuture(Future<String> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(RemoteDeployBundle.message("service.command.outputInterrupted"), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(RemoteDeployBundle.message("service.command.outputReadFailed"), cause);
        }
    }

    /**
     * Keeps the existing single-file upload behavior unless the user explicitly requests a safe staging suffix.
     */
    private String resolveRemoteFileName(String localFileName, DeploymentRequest request) throws IOException {
        if (!request.useRemoteFileSuffix()) {
            return localFileName;
        }
        return localFileName + normalizeRemoteFileSuffix(request.remoteFileSuffix());
    }

    /**
     * Rejects path separators so the suffix can only affect the remote filename and not escape the selected directory.
     */
    private String normalizeRemoteFileSuffix(String remoteFileSuffix) throws IOException {
        String normalized = remoteFileSuffix == null ? "" : remoteFileSuffix.trim();
        if (normalized.isBlank()) {
            throw new IOException(RemoteDeployBundle.message("service.validation.remoteFileSuffixRequired"));
        }
        if (normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0) {
            throw new IOException(RemoteDeployBundle.message("service.validation.remoteFileSuffixInvalid"));
        }
        return normalized;
    }

    private record CommandResult(String stdout, String stderr, int exitCode) {
    }
}
