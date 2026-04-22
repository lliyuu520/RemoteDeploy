package com.liliangyu.remotedeploy.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.ServerConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens authenticated SSH transports for deploy-related features so upload, validation, and browsing share one auth flow.
 */
public final class SshConnectionService {
    /**
     * Connects and authenticates once using either saved secrets or the provided overrides for one-off validation flows.
     */
    public SSHClient openClient(ServerConfig server, ProgressIndicator indicator, String passwordOverride, String passphraseOverride)
        throws IOException {
        indicator.checkCanceled();
        indicator.setText(RemoteDeployBundle.message("service.progress.connecting", server.getHost()));

        SSHClient client = new SSHClient();
        try {
            // Personal-use MVP: trust-on-first-use is postponed so the first version stays frictionless.
            client.addHostKeyVerifier(new PromiscuousVerifier());
            client.connect(server.getHost(), server.getPort());
            indicator.checkCanceled();
            authenticate(client, server, passwordOverride, passphraseOverride);
            return client;
        } catch (IOException exception) {
            try {
                client.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    /**
     * Resolves the configured auth mode and its secret source before features start SFTP or shell work.
     */
    private void authenticate(SSHClient client, ServerConfig server, String passwordOverride, String passphraseOverride) throws IOException {
        if (server.getAuthType() == AuthType.PASSWORD) {
            String password = passwordOverride != null ? passwordOverride.trim() : SecretStorage.loadPassword(server.getId());
            if (password.isBlank()) {
                throw new IOException(RemoteDeployBundle.message("service.auth.passwordMissing", server.getName()));
            }
            client.authPassword(server.getUsername(), password);
            return;
        }

        String keyPath = server.getPrivateKeyPath();
        if (keyPath == null || keyPath.isBlank()) {
            throw new IOException(RemoteDeployBundle.message("service.auth.privateKeyMissing", server.getName()));
        }
        if (!Files.isRegularFile(Path.of(keyPath))) {
            throw new IOException(RemoteDeployBundle.message("service.auth.privateKeyFileMissing", keyPath));
        }

        String passphrase = passphraseOverride != null ? passphraseOverride.trim() : SecretStorage.loadPassphrase(server.getId());
        char[] passphraseChars = passphrase.isBlank() ? null : passphrase.toCharArray();
        client.authPublickey(server.getUsername(), client.loadKeys(keyPath, passphraseChars));
    }
}
