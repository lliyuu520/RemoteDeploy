package com.liliangyu.remotedeploy.service;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;

/**
 * Persists sensitive values in the IDE password store while the XML settings keep only non-secret fields.
 */
public final class SecretStorage {
    private static final String SERVICE_PREFIX = "remote.deploy";

    private SecretStorage() {
    }

    public static String loadPassword(String serverId) {
        return loadSecret(serverId, "password");
    }

    public static void savePassword(String serverId, String password) {
        saveSecret(serverId, "password", password);
    }

    public static String loadPassphrase(String serverId) {
        return loadSecret(serverId, "passphrase");
    }

    public static void savePassphrase(String serverId, String passphrase) {
        saveSecret(serverId, "passphrase", passphrase);
    }

    public static void deleteServerSecrets(String serverId) {
        savePassword(serverId, null);
        savePassphrase(serverId, null);
    }

    private static String loadSecret(String serverId, String key) {
        Credentials credentials = PasswordSafe.getInstance().get(attributes(serverId, key));
        return credentials == null || credentials.getPasswordAsString() == null ? "" : credentials.getPasswordAsString();
    }

    /** Treats blank input as deletion so editing a server cannot leave stale secrets behind. */
    private static void saveSecret(String serverId, String key, String secret) {
        Credentials credentials = (secret == null || secret.isBlank()) ? null : new Credentials(serverId, secret);
        PasswordSafe.getInstance().set(attributes(serverId, key), credentials);
    }

    private static CredentialAttributes attributes(String serverId, String key) {
        return new CredentialAttributes(SERVICE_PREFIX + "." + key, serverId, SecretStorage.class);
    }
}
