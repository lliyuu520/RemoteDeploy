package com.liliangyu.remotedeploy.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.DeploymentRequest;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Collects deploy-time choices while routing server CRUD through the shared manager dialog.
 */
public final class DeployDialog extends DialogWrapper {
    private final @Nullable Project project;
    private final RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();
    private final JComboBox<ServerConfig> serverComboBox = new JComboBox<>();
    private final TextFieldWithBrowseButton localPathField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton remoteDirectoryField = new TextFieldWithBrowseButton();
    private final JCheckBox remoteFileSuffixCheckBox = new JCheckBox();
    private final JBTextField remoteFileSuffixField = new JBTextField();
    private final JBTextArea commandArea = new JBTextArea(6, 60);
    private final JButton manageServersButton = new JButton();
    private final FileChooserDescriptor localPathDescriptor = new FileChooserDescriptor(true, true, false, false, false, false);
    private final JBScrollPane commandScrollPane = new JBScrollPane(commandArea);
    private final JPanel centerPanel = new JPanel(new BorderLayout());
    private final JPanel serverRow = createServerRow();
    private final JPanel remoteFileSuffixRow = createRemoteFileSuffixRow();

    private DeploymentRequest request;

    public DeployDialog(@Nullable Project project, @Nullable String initialLocalPath) {
        super(project);
        this.project = project;

        setTitle(RemoteDeployBundle.message("deploy.dialog.title"));
        setOKButtonText(RemoteDeployBundle.message("deploy.dialog.ok"));
        setResizable(true);

        localPathField.addBrowseFolderListener(new TextBrowseFolderListener(localPathDescriptor, project));
        remoteDirectoryField.addActionListener(event -> browseRemoteDirectory());
        remoteFileSuffixCheckBox.addActionListener(event -> updateRemoteFileSuffixState());
        remoteFileSuffixField.getEmptyText().setText(".tmp");
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        commandScrollPane.setPreferredSize(JBUI.size(0, 160));
        serverComboBox.addActionListener(event -> updateServerSelectionState());

        if (initialLocalPath != null && !initialLocalPath.isBlank()) {
            localPathField.setText(initialLocalPath);
        } else if (project != null && project.getBasePath() != null) {
            localPathField.setText(project.getBasePath());
        }

        reloadServers(settingsService.getLastServerId());
        init();
        initValidation();
        refreshTexts();
    }

    public @Nullable DeploymentRequest getRequest() {
        return request;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        rebuildCenterPanel();
        return centerPanel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return serverComboBox.getItemCount() == 0 ? manageServersButton : serverComboBox;
    }

    @Override
    protected void doOKAction() {
        ServerConfig server = getSelectedServer();
        if (server == null) {
            return;
        }

        settingsService.setLastServerId(server.getId());
        request = new DeploymentRequest(
            server,
            localPathField.getText(),
            remoteDirectoryField.getText(),
            commandArea.getText(),
            remoteFileSuffixCheckBox.isSelected(),
            remoteFileSuffixField.getText()
        );
        super.doOKAction();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getSelectedServer() == null) {
            return new ValidationInfo(
                RemoteDeployBundle.message("deploy.dialog.validation.serverRequired"),
                serverComboBox.getItemCount() == 0 ? manageServersButton : serverComboBox
            );
        }

        String localPath = localPathField.getText().trim();
        if (localPath.isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.localPathRequired"), localPathField);
        }
        if (!Files.exists(Path.of(localPath))) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.localPathMissing"), localPathField);
        }
        if (remoteFileSuffixCheckBox.isSelected() && Files.isRegularFile(Path.of(localPath))) {
            String suffix = remoteFileSuffixField.getText().trim();
            if (suffix.isEmpty()) {
                return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.remoteFileSuffixRequired"), remoteFileSuffixField);
            }
            if (suffix.indexOf('/') >= 0 || suffix.indexOf('\\') >= 0) {
                return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.remoteFileSuffixInvalid"), remoteFileSuffixField);
            }
        }
        if (remoteDirectoryField.getText().trim().isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.remoteDirectoryRequired"), remoteDirectoryField);
        }
        return null;
    }

    private JPanel createServerRow() {
        manageServersButton.addActionListener(event -> openServerManager());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(serverComboBox, BorderLayout.CENTER);
        row.add(manageServersButton, BorderLayout.EAST);
        return row;
    }

    private JPanel createRemoteFileSuffixRow() {
        remoteFileSuffixField.setColumns(12);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(remoteFileSuffixCheckBox, BorderLayout.CENTER);
        row.add(remoteFileSuffixField, BorderLayout.EAST);
        return row;
    }

    /**
     * Keeps deploy disabled until a stored server exists while preserving the rest of the request fields.
     */
    private void updateServerSelectionState() {
        boolean hasServer = getSelectedServer() != null;
        setOKActionEnabled(hasServer);
        remoteDirectoryField.setButtonEnabled(hasServer);
    }

    /**
     * Keeps the suffix text box passive until the user explicitly opts into staging single-file uploads under another name.
     */
    private void updateRemoteFileSuffixState() {
        remoteFileSuffixField.setEnabled(remoteFileSuffixCheckBox.isSelected());
    }

    /**
     * Opens the shared server manager and refreshes the dropdown after any add/edit/remove operation inside it.
     */
    private void openServerManager() {
        ServerManagementDialog dialog = new ServerManagementDialog(project, getSelectedServerId());
        dialog.show();
        reloadServers(dialog.getSelectedServerId());
    }

    /**
     * Browses the selected server lazily and writes the chosen remote directory back into the editable field.
     */
    private void browseRemoteDirectory() {
        ServerConfig server = getSelectedServer();
        if (server == null) {
            Messages.showErrorDialog(project, RemoteDeployBundle.message("deploy.dialog.validation.serverRequired"), getTitle());
            return;
        }

        RemoteDirectoryChooserDialog dialog = new RemoteDirectoryChooserDialog(project, server, remoteDirectoryField.getText());
        if (dialog.showAndGet()) {
            remoteDirectoryField.setText(dialog.getSelectedDirectory());
        }
    }

    private void reloadServers(@Nullable String preferredServerId) {
        List<ServerConfig> servers = settingsService.getServers();
        DefaultComboBoxModel<ServerConfig> model = new DefaultComboBoxModel<>();
        for (ServerConfig server : servers) {
            model.addElement(server);
        }
        serverComboBox.setModel(model);
        selectServer(preferredServerId, servers);
        updateServerSelectionState();
    }

    private void selectServer(@Nullable String preferredServerId, List<ServerConfig> servers) {
        if (servers.isEmpty()) {
            serverComboBox.setSelectedItem(null);
            return;
        }
        if (preferredServerId != null) {
            for (ServerConfig server : servers) {
                if (preferredServerId.equals(server.getId())) {
                    serverComboBox.setSelectedItem(server);
                    return;
                }
            }
        }
        serverComboBox.setSelectedItem(servers.getFirst());
    }

    private @Nullable ServerConfig getSelectedServer() {
        Object selectedItem = serverComboBox.getSelectedItem();
        return selectedItem instanceof ServerConfig server ? server : null;
    }

    private @Nullable String getSelectedServerId() {
        ServerConfig selectedServer = getSelectedServer();
        return selectedServer == null ? null : selectedServer.getId();
    }

    /**
     * Rebuilds the form labels from the current IDEA language while keeping existing field values and selections intact.
     */
    private void refreshTexts() {
        setTitle(RemoteDeployBundle.message("deploy.dialog.title"));
        setOKButtonText(RemoteDeployBundle.message("deploy.dialog.ok"));
        manageServersButton.setText(RemoteDeployBundle.message("common.manage"));
        remoteFileSuffixCheckBox.setText(RemoteDeployBundle.message("option.remoteFileSuffix"));
        localPathDescriptor.setTitle(RemoteDeployBundle.message("chooser.localPath.title"));
        localPathDescriptor.setDescription(RemoteDeployBundle.message("chooser.localPath.description"));
        updateRemoteFileSuffixState();
        rebuildCenterPanel();
    }

    private void rebuildCenterPanel() {
        centerPanel.removeAll();
        centerPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("field.server"), serverRow)
                .addLabeledComponent(RemoteDeployBundle.message("field.localPath"), localPathField)
                .addLabeledComponent(RemoteDeployBundle.message("field.remoteFileSuffix"), remoteFileSuffixRow)
                .addLabeledComponent(RemoteDeployBundle.message("field.remoteDirectory"), remoteDirectoryField)
                .addLabeledComponentFillVertically(RemoteDeployBundle.message("field.remoteCommand"), commandScrollPane)
                .getPanel(),
            BorderLayout.CENTER
        );
        centerPanel.revalidate();
        centerPanel.repaint();
    }
}
