package com.liliangyu.remotedeploy.ui;

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
    private final JPanel authDetailsPanel = new JPanel(new CardLayout());

    private Result result;

    public ServerConfigDialog(@Nullable Project project, @Nullable ServerConfig existingConfig) {
        super(project);
        this.project = project;
        this.existingConfig = existingConfig == null ? null : new ServerConfig(existingConfig);

        setTitle(existingConfig == null ? "Add Server" : "Edit Server");
        setOKButtonText("Save");
        setResizable(true);

        var keyDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
        keyDescriptor.setTitle("Select Private Key");
        keyDescriptor.setDescription("Choose an existing SSH private key file.");
        keyPathField.addBrowseFolderListener(new TextBrowseFolderListener(keyDescriptor, project));
        authTypeComboBox.addActionListener(event -> updateAuthDetailsCard());

        loadInitialValues();
        init();
        updateAuthDetailsCard();
        initValidation();
    }

    public @Nullable Result getResult() {
        return result;
    }

    @Override
    protected Action[] createLeftSideActions() {
        return new Action[]{new AbstractAction("Test Connection") {
            @Override
            public void actionPerformed(ActionEvent event) {
                testConnection();
            }
        }};
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        authDetailsPanel.add(FormBuilder.createFormBuilder().addLabeledComponent("Password:", passwordField).getPanel(), PASSWORD_CARD);
        authDetailsPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent("Private key:", keyPathField)
                .addLabeledComponent("Passphrase:", passphraseField)
                .getPanel(),
            KEY_CARD
        );

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("Host:", hostField)
            .addLabeledComponent("Port:", portSpinner)
            .addLabeledComponent("Username:", usernameField)
            .addLabeledComponent("Authentication:", authTypeComboBox)
            .addComponent(authDetailsPanel)
            .getPanel();
    }

    @Override
    protected void doOKAction() {
        result = new Result(buildServerFromFields(), readPassword(), readPassphrase());
        super.doOKAction();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo("Name is required.", nameField);
        }
        if (hostField.getText().trim().isEmpty()) {
            return new ValidationInfo("Host is required.", hostField);
        }
        if (usernameField.getText().trim().isEmpty()) {
            return new ValidationInfo("Username is required.", usernameField);
        }
        if ((Integer) portSpinner.getValue() <= 0) {
            return new ValidationInfo("Port must be greater than 0.", portSpinner);
        }

        AuthType authType = (AuthType) authTypeComboBox.getSelectedItem();
        if (authType == AuthType.PASSWORD && readPassword().isBlank()) {
            return new ValidationInfo("Password is required for password authentication.", passwordField);
        }
        if (authType == AuthType.PRIVATE_KEY) {
            String keyPath = keyPathField.getText().trim();
            if (keyPath.isEmpty()) {
                return new ValidationInfo("Private key path is required for key authentication.", keyPathField);
            }
            if (!Files.isRegularFile(Path.of(keyPath))) {
                return new ValidationInfo("Private key file does not exist.", keyPathField);
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
            Messages.showErrorDialog(project, validation.message, "Test Connection");
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
                "Testing SSH Connection",
                true,
                project
            );
            Messages.showInfoMessage(project, "Connected to " + server.getHost() + " successfully.", "Test Connection");
        } catch (ProcessCanceledException ignored) {
            // User canceled the modal progress dialog; keep the editor state unchanged.
        } catch (IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Test Connection Failed");
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

    public record Result(ServerConfig server, String password, String passphrase) {
    }
}
