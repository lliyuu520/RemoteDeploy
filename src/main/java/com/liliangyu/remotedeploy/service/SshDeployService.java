package com.liliangyu.remotedeploy.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.DeploymentException;
import com.liliangyu.remotedeploy.model.DeploymentRequest;
import com.liliangyu.remotedeploy.model.DeploymentResult;
import com.liliangyu.remotedeploy.model.ServerConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

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
    /**
     * Executes the minimum viable deployment flow against a single SSH target.
     */
    public DeploymentResult deploy(DeploymentRequest request, ProgressIndicator indicator) throws IOException {
        Path localPath = Path.of(request.localPath());
        if (!Files.exists(localPath)) {
            throw new IOException("Local path does not exist: " + localPath);
        }

        ServerConfig server = request.server();
        String remoteDirectory = normalizeRemoteDirectory(request.remoteDirectory());
        List<String> uploadedPaths = new ArrayList<>();

        try (SSHClient client = new SSHClient()) {
            indicator.setText("Connecting to " + server.getHost());

            // Personal-use MVP: trust-on-first-use is postponed so the first version stays frictionless.
            client.addHostKeyVerifier(new PromiscuousVerifier());
            client.connect(server.getHost(), server.getPort());
            authenticate(client, server);

            try (SFTPClient sftpClient = client.newSFTPClient()) {
                uploadLocalPath(localPath, remoteDirectory, sftpClient, indicator, uploadedPaths);
            }

            if (request.command().isBlank()) {
                return new DeploymentResult(uploadedPaths, "", "", null);
            }

            indicator.setText("Running remote command");
            CommandResult commandResult = executeCommand(client, request.command());
            DeploymentResult result = new DeploymentResult(uploadedPaths, commandResult.stdout(), commandResult.stderr(), commandResult.exitCode());
            if (!result.commandSucceeded()) {
                throw new DeploymentException("Remote command exited with status " + result.exitCode() + ".", result);
            }
            return result;
        }
    }

    /**
     * Resolves the configured auth mode and the corresponding secret before opening SFTP or shell sessions.
     */
    private void authenticate(SSHClient client, ServerConfig server) throws IOException {
        if (server.getAuthType() == AuthType.PASSWORD) {
            String password = SecretStorage.loadPassword(server.getId());
            if (password.isBlank()) {
                throw new IOException("No password stored for server: " + server.getName());
            }
            client.authPassword(server.getUsername(), password);
            return;
        }

        String keyPath = server.getPrivateKeyPath();
        if (keyPath == null || keyPath.isBlank()) {
            throw new IOException("No private key configured for server: " + server.getName());
        }
        if (!Files.isRegularFile(Path.of(keyPath))) {
            throw new IOException("Private key file does not exist: " + keyPath);
        }

        String passphrase = SecretStorage.loadPassphrase(server.getId());
        char[] passphraseChars = passphrase.isBlank() ? null : passphrase.toCharArray();
        client.authPublickey(server.getUsername(), client.loadKeys(keyPath, passphraseChars));
    }

    /**
     * Treats the remote input as a target directory: files land directly inside it and folders keep their top-level name.
     */
    private void uploadLocalPath(Path localPath, String remoteDirectory, SFTPClient sftpClient, ProgressIndicator indicator,
                                 List<String> uploadedPaths) throws IOException {
        sftpClient.mkdirs(remoteDirectory);

        if (Files.isRegularFile(localPath)) {
            indicator.checkCanceled();
            String remoteFile = joinRemotePath(remoteDirectory, localPath.getFileName().toString());
            indicator.setText("Uploading " + localPath.getFileName());
            sftpClient.put(localPath.toString(), remoteFile);
            indicator.setFraction(1.0d);
            uploadedPaths.add(remoteFile);
            return;
        }

        String remoteRoot = joinRemotePath(remoteDirectory, localPath.getFileName().toString());
        sftpClient.mkdirs(remoteRoot);

        long totalFiles = Files.walk(localPath).filter(Files::isRegularFile).count();
        long uploadedCount = 0;

        try (var paths = Files.walk(localPath)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                indicator.checkCanceled();
                Path relativePath = localPath.relativize(path);
                String remotePath = relativePath.getNameCount() == 0
                    ? remoteRoot
                    : joinRemotePath(remoteRoot, relativePath.toString().replace('\\', '/'));

                if (Files.isDirectory(path)) {
                    sftpClient.mkdirs(remotePath);
                    continue;
                }

                indicator.setText("Uploading " + relativePath);
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
            throw new IOException("Interrupted while reading remote command output.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to read remote command output.", cause);
        }
    }

    private String normalizeRemoteDirectory(String remoteDirectory) throws IOException {
                String normalized = remoteDirectory == null ? "" : remoteDirectory.trim().replace('\\', '/');
        if (normalized.isBlank()) {
            throw new IOException("Remote directory is required.");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String joinRemotePath(String parent, String child) {
        return "/".equals(parent) ? "/" + child : parent + "/" + child;
    }

    private record CommandResult(String stdout, String stderr, int exitCode) {
    }
}
