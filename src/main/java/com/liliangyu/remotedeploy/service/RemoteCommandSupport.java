package com.liliangyu.remotedeploy.service;

/**
 * Builds remote shell snippets so deploy-time commands always run from the selected remote directory.
 */
public final class RemoteCommandSupport {
    private RemoteCommandSupport() {
    }

    /**
     * Runs the user command from the target remote directory without changing the existing non-terminal behavior.
     */
    public static String buildExecCommand(String remoteDirectory, String userCommand) {
        return "sh -lc " + shellQuote(buildDirectoryScript(remoteDirectory, userCommand));
    }

    /**
     * Prepends a safe directory change so relative paths inside custom commands match the configured deploy target.
     */
    private static String buildDirectoryScript(String remoteDirectory, String userCommand) {
        String normalizedDirectory = remoteDirectory == null ? "" : remoteDirectory.trim();
        String normalizedCommand = userCommand == null ? "" : userCommand.trim();
        if (normalizedDirectory.isEmpty()) {
            return normalizedCommand;
        }
        return "cd -- " + shellQuote(normalizedDirectory) + " && " + normalizedCommand;
    }

    /**
     * Quotes literal values for POSIX shells while still letting the user command itself keep full shell syntax.
     */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
