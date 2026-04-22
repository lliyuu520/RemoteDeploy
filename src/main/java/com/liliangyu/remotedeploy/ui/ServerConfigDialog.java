package com.liliangyu.remotedeploy.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.SecretStorage;
import com.liliangyu.remotedeploy.service.SshDeployService;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Edits one reusable SSH target and lets the user verify the SSH credentials before saving it.
 */
public final class ServerConfigDialog extends DialogWrapper {
    private static final String PASSWORD_CARD = "password";
    private static final String KEY_CARD = "key";

    private final @Nullable Project project;
    private final @Nullable ServerConfig existingConfig;
    private final SshDeployService sshDeployService = new SshDeployService();
    private final JBTextField nameField = new JBTextField();
    private final JBTextField hostField = new JBTextField();
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
    private final JBTextField usernameField = new JBTextField();
    private final JComboBox<AuthType> authTypeComboBox = new JComboBox<>(authModel());
    private final JPasswordField passwordField = new JPasswordField();
    private final TextFieldWithBrowseButton keyPathField = new TextFieldWithBrowseButton();
    private final JPasswordField passphraseField = new JPasswordField();
    private final FileChooserDescriptor keyDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
    private final JPanel authDetailsPanel = new JPanel(new CardLayout());
    private final JPanel centerPanel = new JPanel(new java.awt.BorderLayout());
    private final Action testConnectionAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent event) {
            testConnection();
        }
    };

    private Result result;

    public ServerConfigDialog(@Nullable Project project, @Nullable ServerConfig existingConfig) {
        super(project);
        this.project = project;
        this.existingConfig = existingConfig == null ? null : new ServerConfig(existingConfig);

        setTitle(existingConfig == null
            ? RemoteDeployBundle.message("server.dialog.title.add")
            : RemoteDeployBundle.message("server.dialog.title.edit"));
        setOKButtonText(RemoteDeployBundle.message("common.save"));
        setResizable(true);

        keyPathField.addBrowseFolderListener(new TextBrowseFolderListener(keyDescriptor, project));
        authTypeComboBox.addActionListener(event -> updateAuthDetailsCard());

        loadInitialValues();
        init();
        updateAuthDetailsCard();
        initValidation();
        refreshTexts();
    }

    public @Nullable Result getResult() {
        return result;
    }

    @Override
    protected Action[] createLeftSideActions() {
        return new Action[]{testConnectionAction};
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        rebuildCenterPanel();
        return centerPanel;
    }

    @Override
    protected void doOKAction() {
        result = new Result(buildServerFromFields(), readPassword(), readPassphrase());
        super.doOKAction();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.nameRequired"), nameField);
        }
        if (hostField.getText().trim().isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.hostRequired"), hostField);
        }
        if (usernameField.getText().trim().isEmpty()) {
            return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.usernameRequired"), usernameField);
        }
        if ((Integer) portSpinner.getValue() <= 0) {
            return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.portInvalid"), portSpinner);
        }

        AuthType authType = (AuthType) authTypeComboBox.getSelectedItem();
        if (authType == AuthType.PASSWORD && readPassword().isBlank()) {
            return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.passwordRequired"), passwordField);
        }
        if (authType == AuthType.PRIVATE_KEY) {
            String keyPath = keyPathField.getText().trim();
            if (keyPath.isEmpty()) {
                return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.privateKeyRequired"), keyPathField);
            }
            if (!Files.isRegularFile(Path.of(keyPath))) {
                return new ValidationInfo(RemoteDeployBundle.message("server.dialog.validation.privateKeyMissing"), keyPathField);
            }
        }
        return null;
    }

    private void loadInitialValues() {
        if (existingConfig == null) {
            authTypeComboBox.setSelectedItem(AuthType.PASSWORD);
            return;
        }

        nameField.setText(existingConfig.getName());
        hostField.setText(existingConfig.getHost());
        portSpinner.setValue(existingConfig.getPort());
        usernameField.setText(existingConfig.getUsername());
        authTypeComboBox.setSelectedItem(existingConfig.getAuthType());
        keyPathField.setText(existingConfig.getPrivateKeyPath());
        passwordField.setText(SecretStorage.loadPassword(existingConfig.getId()));
        passphraseField.setText(SecretStorage.loadPassphrase(existingConfig.getId()));
    }

    /**
     * Collects the current form values once so Save and Test Connection evaluate the same server payload.
     */
    private ServerConfig buildServerFromFields() {
        ServerConfig server = existingConfig == null ? new ServerConfig() : new ServerConfig(existingConfig);
        server.setId(server.getId() == null || server.getId().isBlank() ? UUID.randomUUID().toString() : server.getId());
        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());
        server.setPort((Integer) portSpinner.getValue());
        server.setUsername(usernameField.getText().trim());
        server.setAuthType((AuthType) authTypeComboBox.getSelectedItem());
        server.setPrivateKeyPath(keyPathField.getText().trim());
        return server;
    }

    /**
     * Tests the unsaved form values against the remote host so users can catch auth or network issues up front.
     */
    private void testConnection() {
        ValidationInfo validation = doValidate();
        if (validation != null) {
            validation.component.requestFocusInWindow();
            Messages.showErrorDialog(project, validation.message, RemoteDeployBundle.message("server.dialog.testConnection.title"));
            return;
        }

        ServerConfig server = buildServerFromFields();
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> {
                    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                    sshDeployService.testConnection(
                        server,
                        readPassword(),
                        readPassphrase(),
                        indicator == null ? new EmptyProgressIndicator() : indicator
                    );
                    return null;
                },
                RemoteDeployBundle.message("server.dialog.testConnection.progress"),
                true,
                project
            );
            Messages.showInfoMessage(
                project,
                RemoteDeployBundle.message("server.dialog.testConnection.success", server.getHost()),
                RemoteDeployBundle.message("server.dialog.testConnection.title")
            );
        } catch (ProcessCanceledException ignored) {
            // User canceled the modal progress dialog; keep the editor state unchanged.
        } catch (IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), RemoteDeployBundle.message("server.dialog.testConnection.failure"));
        }
    }

    private void updateAuthDetailsCard() {
        CardLayout layout = (CardLayout) authDetailsPanel.getLayout();
        AuthType authType = (AuthType) authTypeComboBox.getSelectedItem();
        layout.show(authDetailsPanel, authType == AuthType.PRIVATE_KEY ? KEY_CARD : PASSWORD_CARD);
    }

    private String readPassword() {
        return new String(passwordField.getPassword()).trim();
    }

    private String readPassphrase() {
        return new String(passphraseField.getPassword()).trim();
    }

    private static ComboBoxModel<AuthType> authModel() {
        return new DefaultComboBoxModel<>(AuthType.values());
    }

    /**
     * Updates dialog labels and chooser metadata from the current IDEA language without discarding the current form values.
     */
    private void refreshTexts() {
        setTitle(existingConfig == null
            ? RemoteDeployBundle.message("server.dialog.title.add")
            : RemoteDeployBundle.message("server.dialog.title.edit"));
        setOKButtonText(RemoteDeployBundle.message("common.save"));
        testConnectionAction.putValue(Action.NAME, RemoteDeployBundle.message("server.dialog.testConnection"));
        keyDescriptor.setTitle(RemoteDeployBundle.message("chooser.privateKey.title"));
        keyDescriptor.setDescription(RemoteDeployBundle.message("chooser.privateKey.description"));
        authTypeComboBox.repaint();
        rebuildAuthDetailsPanel();
        rebuildCenterPanel();
    }

    private void rebuildAuthDetailsPanel() {
        authDetailsPanel.removeAll();
        authDetailsPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("field.password"), passwordField)
                .getPanel(),
            PASSWORD_CARD
        );
        authDetailsPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("field.privateKey"), keyPathField)
                .addLabeledComponent(RemoteDeployBundle.message("field.passphrase"), passphraseField)
                .getPanel(),
            KEY_CARD
        );
        updateAuthDetailsCard();
        authDetailsPanel.revalidate();
        authDetailsPanel.repaint();
    }

    private void rebuildCenterPanel() {
        centerPanel.removeAll();
        centerPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("field.name"), nameField)
                .addLabeledComponent(RemoteDeployBundle.message("field.host"), hostField)
                .addLabeledComponent(RemoteDeployBundle.message("field.port"), portSpinner)
                .addLabeledComponent(RemoteDeployBundle.message("field.username"), usernameField)
                .addLabeledComponent(RemoteDeployBundle.message("field.authentication"), authTypeComboBox)
                .addComponent(authDetailsPanel)
                .getPanel(),
            java.awt.BorderLayout.CENTER
        );
        centerPanel.revalidate();
        centerPanel.repaint();
    }

    public record Result(ServerConfig server, String password, String passphrase) {
    }
}
