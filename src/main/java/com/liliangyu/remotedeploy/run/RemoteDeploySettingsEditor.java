package com.liliangyu.remotedeploy.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.liliangyu.remotedeploy.model.AuthType;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.SecretStorage;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import com.liliangyu.remotedeploy.ui.ServerConfigDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Editor UI for configuring stored servers and deploy settings inside Run/Debug Configurations.
 */
public final class RemoteDeploySettingsEditor extends SettingsEditor<RemoteDeployRunConfiguration> {
    private final Project project;
    private final RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();

    private final JComboBox<ServerConfig> serverComboBox = new JComboBox<>();
    private final TextFieldWithBrowseButton localPathField = new TextFieldWithBrowseButton();
    private final JBTextField remoteDirectoryField = new JBTextField();
    private final JComboBox<String> deployCommandComboBox = createEditableCommandComboBox();
    private final JComboBox<String> afterRemoteCommandComboBox = createEditableCommandComboBox();

    public RemoteDeploySettingsEditor(Project project) {
        this.project = project;
    }

    @Override
    protected void resetEditorFrom(@NotNull RemoteDeployRunConfiguration configuration) {
        reloadServers(configuration.getServerId());
        localPathField.setText(configuration.getLocalPath());
        remoteDirectoryField.setText(configuration.getRemoteDirectory());
        reloadCommandTemplates(configuration.getCommand(), configuration.getAfterTerminalCommand(), true);
    }

    @Override
    protected void applyEditorTo(@NotNull RemoteDeployRunConfiguration configuration) {
        ServerConfig selected = getSelectedServer();
        configuration.setServerId(selected == null ? "" : selected.getId());
        configuration.setLocalPath(localPathField.getText().trim());
        configuration.setRemoteDirectory(remoteDirectoryField.getText().trim());
        String deployCommand = getCommandValue(deployCommandComboBox);
        String afterRemoteCommand = getCommandValue(afterRemoteCommandComboBox);
        configuration.setCommand(deployCommand);
        configuration.setAfterTerminalCommand(afterRemoteCommand);
        settingsService.rememberDeployCommand(deployCommand);
        settingsService.rememberAfterRemoteCommand(afterRemoteCommand);
    }

    @Override
    protected @NotNull JComponent createEditor() {
        FileChooserDescriptor localPathDescriptor = new FileChooserDescriptor(true, true, false, false, false, false);
        localPathDescriptor.setTitle("Select Local File or Folder");
        localPathDescriptor.setDescription("Pick one file or one directory to upload.");
        localPathField.addBrowseFolderListener(new TextBrowseFolderListener(localPathDescriptor, project));

        serverComboBox.addActionListener(event -> {
            applyServerDefaultsIfBlank();
            reloadCommandTemplates(getCommandValue(deployCommandComboBox), getCommandValue(afterRemoteCommandComboBox), false);
        });

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Server:", createServerRow())
            .addLabeledComponent("Local path:", localPathField)
            .addLabeledComponent("Remote directory:", remoteDirectoryField)
            .addLabeledComponent("Deploy command:", deployCommandComboBox)
            .addLabeledComponent("After remote command (Terminal):", afterRemoteCommandComboBox)
            .getPanel();
    }

    private JPanel createServerRow() {
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton removeButton = new JButton("Remove");

        addButton.addActionListener(event -> addServer());
        editButton.addActionListener(event -> editServer());
        removeButton.addActionListener(event -> removeServer());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(serverComboBox, BorderLayout.CENTER);
        row.add(buttonPanel, BorderLayout.EAST);
        return row;
    }

    private void reloadServers(String preferredServerId) {
        List<ServerConfig> servers = settingsService.getServers();
        DefaultComboBoxModel<ServerConfig> model = new DefaultComboBoxModel<>();
        for (ServerConfig server : servers) {
            model.addElement(server);
        }
        serverComboBox.setModel(model);
        selectServer(preferredServerId, servers);
        applyServerDefaultsIfBlank();
    }

    /**
     * Rebuilds the command dropdown suggestions from lightweight templates without overwriting user-entered values.
     */
    private void reloadCommandTemplates(String deployValue, String afterRemoteValue, boolean preferDefaultsWhenBlank) {
        ServerConfig selected = getSelectedServer();
        String deployCurrent = normalizeCommandValue(deployValue);
        if (preferDefaultsWhenBlank && deployCurrent.isEmpty() && selected != null) {
            deployCurrent = normalizeCommandValue(selected.getDeployCommand());
        }

        List<String> deployTemplates = settingsService.getDeployCommandTemplates(
            selected == null ? "" : selected.getDeployCommand(),
            deployCurrent
        );
        List<String> afterRemoteTemplates = settingsService.getAfterRemoteCommandTemplates(afterRemoteValue);

        setCommandTemplates(deployCommandComboBox, deployTemplates, deployCurrent);
        setCommandTemplates(afterRemoteCommandComboBox, afterRemoteTemplates, afterRemoteValue);
    }

    private void selectServer(String preferredServerId, List<ServerConfig> servers) {
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

    private void addServer() {
        ServerConfigDialog dialog = new ServerConfigDialog(project, null);
        if (!dialog.showAndGet() || dialog.getResult() == null) {
            return;
        }
        String deployCommand = getCommandValue(deployCommandComboBox);
        String afterRemoteCommand = getCommandValue(afterRemoteCommandComboBox);
        persistServer(dialog.getResult());
        reloadServers(dialog.getResult().server().getId());
        reloadCommandTemplates(deployCommand, afterRemoteCommand, false);
    }

    private void editServer() {
        ServerConfig selected = getSelectedServer();
        if (selected == null) {
            return;
        }

        ServerConfigDialog dialog = new ServerConfigDialog(project, selected);
        if (!dialog.showAndGet() || dialog.getResult() == null) {
            return;
        }
        String deployCommand = getCommandValue(deployCommandComboBox);
        String afterRemoteCommand = getCommandValue(afterRemoteCommandComboBox);
        persistServer(dialog.getResult());
        reloadServers(dialog.getResult().server().getId());
        reloadCommandTemplates(deployCommand, afterRemoteCommand, false);
    }

    private void removeServer() {
        ServerConfig selected = getSelectedServer();
        if (selected == null) {
            return;
        }

        int answer = Messages.showYesNoDialog(
            project,
            "Remove server '" + selected.getName() + "'?",
            "Remove Server",
            Messages.getQuestionIcon()
        );
        if (answer != Messages.YES) {
            return;
        }

        settingsService.removeServer(selected.getId());
        SecretStorage.deleteServerSecrets(selected.getId());
        String deployCommand = getCommandValue(deployCommandComboBox);
        String afterRemoteCommand = getCommandValue(afterRemoteCommandComboBox);
        reloadServers(settingsService.getLastServerId());
        reloadCommandTemplates(deployCommand, afterRemoteCommand, false);
    }

    private void applyServerDefaultsIfBlank() {
        ServerConfig selected = getSelectedServer();
        if (selected == null) {
            return;
        }

        if (remoteDirectoryField.getText().trim().isEmpty()) {
            remoteDirectoryField.setText(selected.getRemoteDirectory());
        }
        if (getCommandValue(deployCommandComboBox).isEmpty()) {
            setCommandValue(deployCommandComboBox, selected.getDeployCommand());
        }
    }

    private void persistServer(ServerConfigDialog.Result result) {
        ServerConfig server = result.server();
        settingsService.saveServer(server);
        if (server.getAuthType() == AuthType.PASSWORD) {
            SecretStorage.savePassword(server.getId(), result.password());
            SecretStorage.savePassphrase(server.getId(), null);
            return;
        }
        SecretStorage.savePassword(server.getId(), null);
        SecretStorage.savePassphrase(server.getId(), result.passphrase());
    }

    private ServerConfig getSelectedServer() {
        Object selectedItem = serverComboBox.getSelectedItem();
        return selectedItem instanceof ServerConfig server ? server : null;
    }

    private JComboBox<String> createEditableCommandComboBox() {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        comboBox.setPrototypeDisplayValue("A very long remote command template");
        return comboBox;
    }

    private void setCommandTemplates(JComboBox<String> comboBox, List<String> templates, String currentValue) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String template : templates) {
            model.addElement(template);
        }
        comboBox.setModel(model);
        setCommandValue(comboBox, currentValue);
    }

    private String getCommandValue(JComboBox<String> comboBox) {
        Object item = comboBox.isEditable() ? comboBox.getEditor().getItem() : comboBox.getSelectedItem();
        return normalizeCommandValue(item == null ? "" : item.toString());
    }

    private void setCommandValue(JComboBox<String> comboBox, String value) {
        String normalized = normalizeCommandValue(value);
        comboBox.getEditor().setItem(normalized);
        if (!normalized.isEmpty()) {
            comboBox.setSelectedItem(normalized);
        }
    }

    private String normalizeCommandValue(String value) {
        return value == null ? "" : value.trim();
    }
}
