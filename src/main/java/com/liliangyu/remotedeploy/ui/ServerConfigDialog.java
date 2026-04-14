package com.liliangyu.remotedeploy.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.SecretStorage;
import org.jetbrains.annotations.Nullable;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.CardLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Edits one reusable SSH target, including its defaults and whichever secret type the chosen auth mode needs.
 */
public final class ServerConfigDialog extends DialogWrapper {
    private static final String PASSWORD_CARD = "password";
    private static final String KEY_CARD = "key";

    private final @Nullable ServerConfig existingConfig;
    private final JBTextField nameField = new JBTextField();
    private final JBTextField hostField = new JBTextField();
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
    private final JBTextField usernameField = new JBTextField();
    private final JComboBox<AuthType> authTypeComboBox = new JComboBox<>(authModel());
    private final JPasswordField passwordField = new JPasswordField();
    private final TextFieldWithBrowseButton keyPathField = new TextFieldWithBrowseButton();
    private final JPasswordField passphraseField = new JPasswordField();
    private final JBTextField remoteDirectoryField = new JBTextField();
    private final JBTextArea commandArea = new JBTextArea(5, 60);
    private final JPanel authDetailsPanel = new JPanel(new CardLayout());

    private Result result;

    public ServerConfigDialog(@Nullable Project project, @Nullable ServerConfig existingConfig) {
        super(project);
        this.existingConfig = existingConfig == null ? null : new ServerConfig(existingConfig);

        setTitle(existingConfig == null ? "Add Server" : "Edit Server");
        setOKButtonText("Save");
        setResizable(true);

                var keyDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
                keyDescriptor.setTitle("Select Private Key");
                keyDescriptor.setDescription("Choose an existing SSH private key file.");
                keyPathField.addBrowseFolderListener(new TextBrowseFolderListener(keyDescriptor, project));
        authTypeComboBox.addActionListener(event -> updateAuthDetailsCard());
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);

        loadInitialValues();
        init();
        updateAuthDetailsCard();
        initValidation();
    }

    public @Nullable Result getResult() {
        return result;
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

        JBScrollPane commandScrollPane = new JBScrollPane(commandArea);
        commandScrollPane.setPreferredSize(JBUI.size(0, 140));

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("Host:", hostField)
            .addLabeledComponent("Port:", portSpinner)
            .addLabeledComponent("Username:", usernameField)
            .addLabeledComponent("Authentication:", authTypeComboBox)
            .addComponent(authDetailsPanel)
            .addLabeledComponent("Default remote dir:", remoteDirectoryField)
                    .addLabeledComponentFillVertically("Default command:", commandScrollPane)
            .getPanel();
    }

    @Override
    protected void doOKAction() {
        ServerConfig server = existingConfig == null ? new ServerConfig() : new ServerConfig(existingConfig);
        server.setId(server.getId() == null || server.getId().isBlank() ? UUID.randomUUID().toString() : server.getId());
        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());
        server.setPort((Integer) portSpinner.getValue());
        server.setUsername(usernameField.getText().trim());
        server.setAuthType((AuthType) authTypeComboBox.getSelectedItem());
        server.setPrivateKeyPath(keyPathField.getText().trim());
        server.setRemoteDirectory(remoteDirectoryField.getText().trim());
        server.setDeployCommand(commandArea.getText().trim());

        result = new Result(server, readPassword(), readPassphrase());
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
        remoteDirectoryField.setText(existingConfig.getRemoteDirectory());
        commandArea.setText(existingConfig.getDeployCommand());
        passwordField.setText(SecretStorage.loadPassword(existingConfig.getId()));
        passphraseField.setText(SecretStorage.loadPassphrase(existingConfig.getId()));
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
