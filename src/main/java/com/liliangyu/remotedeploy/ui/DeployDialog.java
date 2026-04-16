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
import com.liliangyu.remotedeploy.i18n.UiLanguage;
import com.liliangyu.remotedeploy.model.DeploymentRequest;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.SecretStorage;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Collects the deploy-time choices while still letting the user manage stored targets without leaving the flow.
 */
public final class DeployDialog extends DialogWrapper {
    private final @Nullable Project project;
    private final RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();
    private final JComboBox<UiLanguage> languageComboBox = new JComboBox<>(UiLanguage.values());
    private final JComboBox<ServerConfig> serverComboBox = new JComboBox<>();
    private final TextFieldWithBrowseButton localPathField = new TextFieldWithBrowseButton();
    private final JBTextField remoteDirectoryField = new JBTextField();
    private final JBTextArea commandArea = new JBTextArea(6, 60);
    private final JButton addButton = new JButton();
    private final JButton editButton = new JButton();
    private final JButton removeButton = new JButton();
    private final FileChooserDescriptor localPathDescriptor = new FileChooserDescriptor(true, true, false, false, false, false);
    private final JBScrollPane commandScrollPane = new JBScrollPane(commandArea);
    private final JPanel centerPanel = new JPanel(new BorderLayout());
    private final JPanel serverRow = createServerRow();

    private DeploymentRequest request;

    public DeployDialog(@Nullable Project project, @Nullable String initialLocalPath) {
        super(project);
        this.project = project;

        setTitle(RemoteDeployBundle.message("deploy.dialog.title"));
        setOKButtonText(RemoteDeployBundle.message("deploy.dialog.ok"));
        setResizable(true);

        localPathField.addBrowseFolderListener(new TextBrowseFolderListener(localPathDescriptor, project));
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        commandScrollPane.setPreferredSize(JBUI.size(0, 160));
        languageComboBox.setSelectedItem(settingsService.getUiLanguage());
        languageComboBox.addActionListener(event -> applySelectedLanguage());
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
        return serverComboBox.getItemCount() == 0 ? localPathField.getTextField() : serverComboBox;
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
            commandArea.getText()
        );
        super.doOKAction();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getSelectedServer() == null) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.serverRequired"), serverComboBox);
        }

        String localPath = localPathField.getText().trim();
        if (localPath.isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.localPathRequired"), localPathField);
        }
        if (!Files.exists(Path.of(localPath))) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.localPathMissing"), localPathField);
        }
        if (remoteDirectoryField.getText().trim().isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("deploy.dialog.validation.remoteDirectoryRequired"), remoteDirectoryField);
        }
        return null;
    }

    private JPanel createServerRow() {
        addButton.addActionListener(event -> addServer());
        editButton.addActionListener(event -> editSelectedServer());
        removeButton.addActionListener(event -> removeSelectedServer());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(serverComboBox, BorderLayout.CENTER);
        row.add(buttonPanel, BorderLayout.EAST);
        return row;
    }

    private void addServer() {
        ServerConfigDialog dialog = new ServerConfigDialog(project, null);
        if (!dialog.showAndGet() || dialog.getResult() == null) {
            return;
        }
        persistServer(dialog.getResult());
        reloadServers(dialog.getResult().server().getId());
    }

    private void editSelectedServer() {
        ServerConfig selectedServer = getSelectedServer();
        if (selectedServer == null) {
            return;
        }

        ServerConfigDialog dialog = new ServerConfigDialog(project, selectedServer);
        if (!dialog.showAndGet() || dialog.getResult() == null) {
            return;
        }
        persistServer(dialog.getResult());
        reloadServers(dialog.getResult().server().getId());
    }

    private void removeSelectedServer() {
        ServerConfig selectedServer = getSelectedServer();
        if (selectedServer == null) {
            return;
        }

        int answer = Messages.showYesNoDialog(
            project,
            RemoteDeployBundle.message("deploy.dialog.removeServer.message", selectedServer.getName()),
            RemoteDeployBundle.message("deploy.dialog.removeServer.title"),
            Messages.getQuestionIcon()
        );
        if (answer != Messages.YES) {
            return;
        }

        settingsService.removeServer(selectedServer.getId());
        SecretStorage.deleteServerSecrets(selectedServer.getId());
        reloadServers(settingsService.getLastServerId());
    }

    /**
     * Keeps the dialog actionable only when a server exists, without silently injecting hidden server-level defaults.
     */
    private void updateServerSelectionState() {
        ServerConfig selectedServer = getSelectedServer();
        if (selectedServer == null) {
            remoteDirectoryField.setText("");
            commandArea.setText("");
            setOKActionEnabled(false);
            return;
        }
        setOKActionEnabled(true);
    }

    private void persistServer(ServerConfigDialog.Result result) {
        ServerConfig server = result.server();
        settingsService.saveServer(server);
        if (server.getAuthType() == com.liliangyu.remotedeploy.model.AuthType.PASSWORD) {
            SecretStorage.savePassword(server.getId(), result.password());
            SecretStorage.savePassphrase(server.getId(), null);
            return;
        }
        SecretStorage.savePassword(server.getId(), null);
        SecretStorage.savePassphrase(server.getId(), result.passphrase());
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

    private void applySelectedLanguage() {
        UiLanguage selected = (UiLanguage) languageComboBox.getSelectedItem();
        settingsService.setUiLanguage(selected);
        refreshTexts();
    }

    /**
     * Rebuilds the form labels from the current language while keeping existing field values and selections intact.
     */
    private void refreshTexts() {
        setTitle(RemoteDeployBundle.message("deploy.dialog.title"));
        setOKButtonText(RemoteDeployBundle.message("deploy.dialog.ok"));
        addButton.setText(RemoteDeployBundle.message("common.add"));
        editButton.setText(RemoteDeployBundle.message("common.edit"));
        removeButton.setText(RemoteDeployBundle.message("common.remove"));
        localPathDescriptor.setTitle(RemoteDeployBundle.message("chooser.localPath.title"));
        localPathDescriptor.setDescription(RemoteDeployBundle.message("chooser.localPath.description"));
        rebuildCenterPanel();
    }

    private void rebuildCenterPanel() {
        centerPanel.removeAll();
        centerPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("common.language"), languageComboBox)
                .addLabeledComponent(RemoteDeployBundle.message("field.server"), serverRow)
                .addLabeledComponent(RemoteDeployBundle.message("field.localPath"), localPathField)
                .addLabeledComponent(RemoteDeployBundle.message("field.remoteDirectory"), remoteDirectoryField)
                .addLabeledComponentFillVertically(RemoteDeployBundle.message("field.remoteCommand"), commandScrollPane)
                .getPanel(),
            BorderLayout.CENTER
        );
        centerPanel.revalidate();
        centerPanel.repaint();
    }
}
