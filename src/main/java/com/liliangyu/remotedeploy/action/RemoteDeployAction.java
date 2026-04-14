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

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        DeployDialog dialog = new DeployDialog(project, detectInitialLocalPath(event));
        if (!dialog.showAndGet() || dialog.getRequest() == null) {
            return;
        }

        DeploymentRequest request = dialog.getRequest();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Remote Deploy", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                try {
                    DeploymentResult result = sshDeployService.deploy(request, indicator);
                    invokeLater(() -> showSuccess(project, request, result));
                } catch (ProcessCanceledException ignored) {
                    invokeLater(() -> showNotification(project, "Deployment canceled", "Remote deploy was canceled.", NotificationType.WARNING));
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
        String content = "Uploaded " + result.uploadedPaths().size() + " item(s) to "
            + request.server().getName() + "." + (result.exitCode() == null ? "" : " Command exit code: " + result.exitCode() + ".");
                showNotification(project, "Deployment finished", content, NotificationType.INFORMATION);
        if (result.hasOutput()) {
            new ExecutionOutputDialog(project, "Remote Command Output", formatOutput(result)).show();
        }
    }

    private void showDeploymentFailure(Project project, DeploymentException exception) {
        DeploymentResult result = exception.getResult();
        String content = "Upload completed, but the remote command failed with exit code " + result.exitCode() + ".";
                showNotification(project, "Deployment failed", content, NotificationType.ERROR);
        new ExecutionOutputDialog(project, "Remote Command Output", formatOutput(result)).show();
    }

    private void showUnexpectedFailure(Project project, Exception exception) {
        Messages.showErrorDialog(project, exception.getMessage(), "Remote Deploy Failed");
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
        StringBuilder builder = new StringBuilder();
        builder.append("Exit code: ").append(result.exitCode() == null ? "n/a" : result.exitCode()).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("STDOUT").append(System.lineSeparator()).append("------").append(System.lineSeparator());
        builder.append(result.stdout().isBlank() ? "<empty>" : result.stdout()).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("STDERR").append(System.lineSeparator()).append("------").append(System.lineSeparator());
        builder.append(result.stderr().isBlank() ? "<empty>" : result.stderr());
        return builder.toString();
    }
}
