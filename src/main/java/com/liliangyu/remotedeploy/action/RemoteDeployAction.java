package com.liliangyu.remotedeploy.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.DeploymentException;
import com.liliangyu.remotedeploy.model.DeploymentRequest;
import com.liliangyu.remotedeploy.model.DeploymentResult;
import com.liliangyu.remotedeploy.service.SshDeployService;
import com.liliangyu.remotedeploy.ui.DeployDialog;
import com.liliangyu.remotedeploy.ui.ExecutionOutputDialog;
import org.jetbrains.annotations.NotNull;

/** Entry action that opens the deploy flow and dispatches the network work to a background task. */
public final class RemoteDeployAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(RemoteDeployAction.class);
    private static final String NOTIFICATION_GROUP_ID = "Remote Deploy";

    private final SshDeployService sshDeployService = new SshDeployService();

    public RemoteDeployAction() {
        super(
            RemoteDeployBundle.message("action.remoteDeploy.text"),
            RemoteDeployBundle.message("action.remoteDeploy.description"),
            null
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setText(RemoteDeployBundle.message("action.remoteDeploy.text"));
        event.getPresentation().setDescription(RemoteDeployBundle.message("action.remoteDeploy.description"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        DeployDialog dialog = new DeployDialog(project, detectInitialLocalPath(event));
        if (!dialog.showAndGet() || dialog.getRequest() == null) {
            return;
        }

        DeploymentRequest request = dialog.getRequest();
        ProgressManager.getInstance().run(new Task.Backgroundable(
            project,
            RemoteDeployBundle.message("action.remoteDeploy.progress"),
            true
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                try {
                    DeploymentResult result = sshDeployService.deploy(request, indicator);
                    invokeLater(() -> showSuccess(project, request, result));
                } catch (ProcessCanceledException ignored) {
                    invokeLater(() -> showNotification(
                        project,
                        RemoteDeployBundle.message("action.remoteDeploy.notification.canceled.title"),
                        RemoteDeployBundle.message("action.remoteDeploy.notification.canceled.content"),
                        NotificationType.WARNING
                    ));
                } catch (DeploymentException exception) {
                    LOG.warn("Remote command failed after upload.", exception);
                    invokeLater(() -> showDeploymentFailure(project, exception));
                } catch (Exception exception) {
                    LOG.warn("Remote deploy failed.", exception);
                    invokeLater(() -> showUnexpectedFailure(project, exception));
                }
            }
        });
    }

    private void showSuccess(Project project, DeploymentRequest request, DeploymentResult result) {
        String exitCodeText = result.exitCode() == null
            ? ""
            : RemoteDeployBundle.message("action.remoteDeploy.notification.success.exitCode", result.exitCode());
        String content = RemoteDeployBundle.message(
            "action.remoteDeploy.notification.success.content",
            result.uploadedPaths().size(),
            request.server().getName(),
            exitCodeText
        );
        showNotification(
            project,
            RemoteDeployBundle.message("action.remoteDeploy.notification.success.title"),
            content,
            NotificationType.INFORMATION
        );
        if (result.hasOutput()) {
            new ExecutionOutputDialog(project, RemoteDeployBundle.message("action.remoteDeploy.output.title"), formatOutput(result)).show();
        }
    }

    private void showDeploymentFailure(Project project, DeploymentException exception) {
        DeploymentResult result = exception.getResult();
        String content = RemoteDeployBundle.message("action.remoteDeploy.notification.failure.content", result.exitCode());
        showNotification(
            project,
            RemoteDeployBundle.message("action.remoteDeploy.notification.failure.title"),
            content,
            NotificationType.ERROR
        );
        new ExecutionOutputDialog(project, RemoteDeployBundle.message("action.remoteDeploy.output.title"), formatOutput(result)).show();
    }

    private void showUnexpectedFailure(Project project, Exception exception) {
        Messages.showErrorDialog(
            project,
            exception.getMessage(),
            RemoteDeployBundle.message("action.remoteDeploy.unexpectedFailure.title")
        );
    }

    private void showNotification(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, type)
            .notify(project);
    }

    /**
     * Prefills the dialog from the current project view selection so the common case starts with one fewer step.
     */
    private String detectInitialLocalPath(AnActionEvent event) {
        VirtualFile selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE);
        return selectedFile == null ? "" : selectedFile.getPath();
    }

    private void invokeLater(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
    }

    private String formatOutput(DeploymentResult result) {
        String exitCodeText = result.exitCode() == null
            ? RemoteDeployBundle.message("run.profile.exitCode.na")
            : String.valueOf(result.exitCode());
        StringBuilder builder = new StringBuilder();
        builder.append(RemoteDeployBundle.message("action.remoteDeploy.output.exitCode", exitCodeText))
            .append(System.lineSeparator())
            .append(System.lineSeparator());
        builder.append(RemoteDeployBundle.message("action.remoteDeploy.output.stdout"))
            .append(System.lineSeparator())
            .append(RemoteDeployBundle.message("action.remoteDeploy.output.separator"))
            .append(System.lineSeparator());
        builder.append(result.stdout().isBlank() ? RemoteDeployBundle.message("action.remoteDeploy.output.empty") : result.stdout())
            .append(System.lineSeparator())
            .append(System.lineSeparator());
        builder.append(RemoteDeployBundle.message("action.remoteDeploy.output.stderr"))
            .append(System.lineSeparator())
            .append(RemoteDeployBundle.message("action.remoteDeploy.output.separator"))
            .append(System.lineSeparator());
        builder.append(result.stderr().isBlank() ? RemoteDeployBundle.message("action.remoteDeploy.output.empty") : result.stderr());
        return builder.toString();
    }
}
