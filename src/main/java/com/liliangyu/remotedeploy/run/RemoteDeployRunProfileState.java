package com.liliangyu.remotedeploy.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.liliangyu.remotedeploy.model.DeploymentException;
import com.liliangyu.remotedeploy.model.DeploymentRequest;
import com.liliangyu.remotedeploy.model.DeploymentResult;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.SshDeployService;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a remote deploy run configuration and streams results to the Run tool window console.
 */
public final class RemoteDeployRunProfileState implements RunProfileState {
    private static final Logger LOG = Logger.getInstance(RemoteDeployRunProfileState.class);

    private final ExecutionEnvironment environment;
    private final RemoteDeployRunConfiguration configuration;

    public RemoteDeployRunProfileState(ExecutionEnvironment environment, RemoteDeployRunConfiguration configuration) {
        this.environment = environment;
        this.configuration = configuration;
    }

    @Override
    public ExecutionResult execute(com.intellij.execution.Executor executor, @NotNull com.intellij.execution.runners.ProgramRunner<?> runner)
        throws ExecutionException {
        Project project = environment.getProject();
        ConsoleView console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .getConsole();

        RemoteDeployProcessHandler handler = new RemoteDeployProcessHandler();
        console.attachToProcess(handler);

        handler.startNotify();
        ApplicationManager.getApplication().executeOnPooledThread(() -> runDeploy(handler));

        return new DefaultExecutionResult(console, handler);
    }

    private void runDeploy(RemoteDeployProcessHandler handler) {
        ProgressIndicator indicator = handler.getIndicator();
        Project project = environment.getProject();
        try {
            RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();
            Optional<ServerConfig> serverConfig = settingsService.findServer(configuration.getServerId());
            if (serverConfig.isEmpty()) {
                printSystem(handler, "Server not found. Open the configuration and select a valid server.");
                handler.terminate(1);
                return;
            }

            ServerConfig server = serverConfig.get();
            String localPath = expandMacros(configuration.getLocalPath());
            String remoteDirectory = pickValue(configuration.getRemoteDirectory(), server.getRemoteDirectory());
            String command = pickValue(configuration.getCommand(), server.getDeployCommand());
            String afterRemoteCommand = expandMacros(configuration.getAfterTerminalCommand());

            printSystem(handler, "Connecting to " + server.getName() + " (" + server.getHost() + ")");

            DeploymentRequest request = new DeploymentRequest(server, localPath, remoteDirectory, command);
            DeploymentResult result = new SshDeployService().deploy(request, indicator);

            printSystem(handler, "Upload finished. Uploaded " + result.uploadedPaths().size() + " item(s).");
            printCommandOutput(handler, result);

            if (!afterRemoteCommand.isBlank()) {
                printSystem(handler, "Opening IDEA Terminal for post-deploy remote command.");
                new IdeaTerminalCommandExecutor(project).openAndExecute(
                    buildAfterTerminalTabTitle(server),
                    localPath,
                    server,
                    afterRemoteCommand
                );
            }
            handler.terminate(result.commandSucceeded() ? 0 : exitCodeOrDefault(result, 1));
        } catch (ProcessCanceledException canceled) {
            printSystem(handler, "Deployment canceled.");
            handler.terminate(1);
        } catch (DeploymentException deploymentException) {
            printSystem(handler, deploymentException.getMessage());
            printCommandOutput(handler, deploymentException.getResult());
            handler.terminate(exitCodeOrDefault(deploymentException.getResult(), 1));
        } catch (Macro.ExecutionCancelledException macroException) {
            printSystem(handler, "Macro expansion was canceled.");
            handler.terminate(1);
        } catch (Exception exception) {
            LOG.warn("Remote deploy failed.", exception);
            printError(handler, "Remote deploy failed: " + exception.getMessage());
            handler.terminate(1);
        }
    }

    /**
     * Gives the follow-up terminal tab a predictable title so multiple deploy sessions stay recognizable.
     */
    private String buildAfterTerminalTabTitle(ServerConfig server) {
        String serverName = server.getName() == null || server.getName().isBlank() ? server.getHost() : server.getName().trim();
        return "Remote Deploy - " + serverName;
    }

    private String expandMacros(String value) throws Macro.ExecutionCancelledException {
        String raw = value == null ? "" : value;
        DataContext context = environment.getDataContext();
        return MacroManager.getInstance().expandMacrosInString(raw, true, context == null ? DataContext.EMPTY_CONTEXT : context);
    }

    private static String pickValue(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private void printCommandOutput(RemoteDeployProcessHandler handler, DeploymentResult result) {
        if (!result.hasOutput()) {
            return;
        }
        printSystem(handler, "Exit code: " + (result.exitCode() == null ? "n/a" : result.exitCode()));
        if (!result.stdout().isBlank()) {
            printStdout(handler, result.stdout());
        }
        if (!result.stderr().isBlank()) {
            printStderr(handler, result.stderr());
        }
    }

    private void printSystem(RemoteDeployProcessHandler handler, String message) {
        handler.notifyTextAvailable(message + System.lineSeparator(), ProcessOutputTypes.SYSTEM);
    }

    private void printStdout(RemoteDeployProcessHandler handler, String message) {
        handler.notifyTextAvailable(message + System.lineSeparator(), ProcessOutputTypes.STDOUT);
    }

    private void printStderr(RemoteDeployProcessHandler handler, String message) {
        handler.notifyTextAvailable(message + System.lineSeparator(), ProcessOutputTypes.STDERR);
    }

    private void printError(RemoteDeployProcessHandler handler, String message) {
        handler.notifyTextAvailable(message + System.lineSeparator(), ProcessOutputTypes.STDERR);
    }

    private int exitCodeOrDefault(DeploymentResult result, int fallback) {
        return result.exitCode() == null ? fallback : result.exitCode();
    }

    private static final class RemoteDeployProcessHandler extends ProcessHandler {
        private final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        /**
         * Progress indicator shared with SshDeployService to allow cooperative cancellation.
         */
        public ProgressIndicator getIndicator() {
            return indicator;
        }

        @Override
        protected void destroyProcessImpl() {
            indicator.cancel();
            terminate(1);
        }

        @Override
        protected void detachProcessImpl() {
            destroyProcessImpl();
        }

        @Override
        public boolean detachIsDefault() {
            return false;
        }

        @Override
        public java.io.OutputStream getProcessInput() {
            return null;
        }

        /**
         * Ensures termination notification happens only once, regardless of error or cancellation paths.
         */
        public void terminate(int exitCode) {
            if (terminated.compareAndSet(false, true)) {
                notifyProcessTerminated(exitCode);
            }
        }
    }
}
