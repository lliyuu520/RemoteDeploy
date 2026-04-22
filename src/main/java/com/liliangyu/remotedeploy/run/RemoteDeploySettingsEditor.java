package com.liliangyu.remotedeploy.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import com.liliangyu.remotedeploy.ui.RemoteDirectoryChooserDialog;
import com.liliangyu.remotedeploy.ui.ServerManagementDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Editor UI for choosing a stored server plus the deploy settings attached to one Run/Debug Configuration.
 */
public final class RemoteDeploySettingsEditor extends SettingsEditor<RemoteDeployRunConfiguration> {
    private final Project project;
    private final RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();

    private final JComboBox<ServerConfig> serverComboBox = new JComboBox<>();
    private final TextFieldWithBrowseButton localPathField = new TextFieldWithBrowseButton();
    private final JCheckBox remoteFileSuffixCheckBox = new JCheckBox();
    private final JBTextField remoteFileSuffixField = new JBTextField();
    private final TextFieldWithBrowseButton remoteDirectoryField = new TextFieldWithBrowseButton();
    private final JComboBox<String> deployCommandComboBox = createEditableCommandComboBox();
    private final JButton manageServersButton = new JButton();
    private final FileChooserDescriptor localPathDescriptor = new FileChooserDescriptor(true, true, false, false, false, false);
    private final JPanel editorPanel = new JPanel(new BorderLayout());
    private final JPanel serverRow = createServerRow();
    private final JPanel remoteFileSuffixRow = createRemoteFileSuffixRow();
    private final JPanel deployCommandRow = createStretchRow(deployCommandComboBox);

    public RemoteDeploySettingsEditor(Project project) {
        this.project = project;
        remoteFileSuffixCheckBox.addActionListener(event -> updateRemoteFileSuffixState());
        remoteFileSuffixField.getEmptyText().setText(".tmp");
    }

    @Override
    protected void resetEditorFrom(@NotNull RemoteDeployRunConfiguration configuration) {
        reloadServers(configuration.getServerId());
        localPathField.setText(configuration.getLocalPath());
        remoteFileSuffixCheckBox.setSelected(configuration.isUseRemoteFileSuffix());
        remoteFileSuffixField.setText(configuration.getRemoteFileSuffix());
        updateRemoteFileSuffixState();
        remoteDirectoryField.setText(configuration.getRemoteDirectory());
        reloadCommandTemplates(configuration.getCommand());
    }

    @Override
    protected void applyEditorTo(@NotNull RemoteDeployRunConfiguration configuration) {
        ServerConfig selected = getSelectedServer();
        configuration.setServerId(selected == null ? "" : selected.getId());
        configuration.setLocalPath(localPathField.getText().trim());
        configuration.setUseRemoteFileSuffix(remoteFileSuffixCheckBox.isSelected());
        configuration.setRemoteFileSuffix(remoteFileSuffixField.getText().trim());
        configuration.setRemoteDirectory(remoteDirectoryField.getText().trim());
        String deployCommand = getCommandValue(deployCommandComboBox);
        configuration.setCommand(deployCommand);
        settingsService.rememberDeployCommand(deployCommand);
    }

    @Override
    protected @NotNull JComponent createEditor() {
        localPathField.addBrowseFolderListener(new TextBrowseFolderListener(localPathDescriptor, project));
        remoteDirectoryField.addActionListener(event -> browseRemoteDirectory());

        serverComboBox.setPrototypeDisplayValue(createServerPrototype());
        serverComboBox.addActionListener(event -> {
            reloadCommandTemplates(getCommandValue(deployCommandComboBox));
            updateRemoteDirectoryBrowseState();
        });

        refreshTexts();
        updateRemoteDirectoryBrowseState();
        return editorPanel;
    }

    private JPanel createServerRow() {
        manageServersButton.addActionListener(event -> openServerManager());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(serverComboBox, BorderLayout.CENTER);
        row.add(manageServersButton, BorderLayout.EAST);
        return row;
    }

    /**
     * FormBuilder does not horizontally stretch JComboBox by default, so wrap it in a row panel that can fill the column.
     */
    private JPanel createStretchRow(JComponent component) {
        JPanel row = new JPanel(new BorderLayout());
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    private JPanel createRemoteFileSuffixRow() {
        remoteFileSuffixField.setColumns(12);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(remoteFileSuffixCheckBox, BorderLayout.CENTER);
        row.add(remoteFileSuffixField, BorderLayout.EAST);
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
        updateRemoteDirectoryBrowseState();
    }

    /**
     * Rebuilds the command dropdown suggestions from lightweight history without overwriting user-entered values.
     */
    private void reloadCommandTemplates(String deployValue) {
        String deployCurrent = normalizeCommandValue(deployValue);
        List<String> deployTemplates = settingsService.getDeployCommandTemplates(deployCurrent);
        setCommandTemplates(deployCommandComboBox, deployTemplates, deployCurrent);
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

    /**
     * Opens the shared server manager so this editor only owns server selection, not server CRUD.
     */
    private void openServerManager() {
        String deployCommand = getCommandValue(deployCommandComboBox);
        ServerManagementDialog dialog = new ServerManagementDialog(project, getSelectedServerId());
        dialog.show();
        reloadServers(dialog.getSelectedServerId());
        reloadCommandTemplates(deployCommand);
    }

    /**
     * Opens the shared remote-directory browser so run configurations can reuse the same path selection flow as ad-hoc deploys.
     */
    private void browseRemoteDirectory() {
        ServerConfig server = getSelectedServer();
        if (server == null) {
            Messages.showErrorDialog(
                project,
                RemoteDeployBundle.message("run.configuration.validation.serverRequired"),
                RemoteDeployBundle.message("run.config.type.displayName")
            );
            return;
        }

        RemoteDirectoryChooserDialog dialog = new RemoteDirectoryChooserDialog(project, server, remoteDirectoryField.getText());
        if (dialog.showAndGet()) {
            remoteDirectoryField.setText(dialog.getSelectedDirectory());
        }
    }

    private ServerConfig getSelectedServer() {
        Object selectedItem = serverComboBox.getSelectedItem();
        return selectedItem instanceof ServerConfig server ? server : null;
    }

    private String getSelectedServerId() {
        ServerConfig selected = getSelectedServer();
        return selected == null ? "" : selected.getId();
    }

    private JComboBox<String> createEditableCommandComboBox() {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        comboBox.setPrototypeDisplayValue(RemoteDeployBundle.message("run.editor.command.prototype"));
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

    private void updateRemoteDirectoryBrowseState() {
        remoteDirectoryField.setButtonEnabled(getSelectedServer() != null);
    }

    /**
     * Keeps the suffix field dormant until the configuration explicitly opts into staging single-file uploads under a temp name.
     */
    private void updateRemoteFileSuffixState() {
        remoteFileSuffixField.setEnabled(remoteFileSuffixCheckBox.isSelected());
    }

    private ServerConfig createServerPrototype() {
        ServerConfig prototype = new ServerConfig();
        prototype.setName("deploy-prod-long.example.com");
        return prototype;
    }

    /**
     * Rebuilds the editor labels and tooltips in place so the Run Configuration UI follows the current IDEA language.
     */
    private void refreshTexts() {
        localPathDescriptor.setTitle(RemoteDeployBundle.message("chooser.localPath.title"));
        localPathDescriptor.setDescription(RemoteDeployBundle.message("chooser.localPath.description"));
        manageServersButton.setText(RemoteDeployBundle.message("common.manage"));
        remoteFileSuffixCheckBox.setText(RemoteDeployBundle.message("option.remoteFileSuffix"));
        deployCommandComboBox.setPrototypeDisplayValue(RemoteDeployBundle.message("run.editor.command.prototype"));
        updateRemoteFileSuffixState();
        rebuildEditorPanel();
    }

    private void rebuildEditorPanel() {
        editorPanel.removeAll();
        editorPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("field.server"), serverRow)
                .addLabeledComponent(RemoteDeployBundle.message("field.localPath"), localPathField)
                .addLabeledComponent(RemoteDeployBundle.message("field.remoteFileSuffix"), remoteFileSuffixRow)
                .addLabeledComponent(RemoteDeployBundle.message("field.remoteDirectory"), remoteDirectoryField)
                .addLabeledComponent(RemoteDeployBundle.message("field.deployCommand"), deployCommandRow)
                .getPanel(),
            BorderLayout.CENTER
        );
        editorPanel.revalidate();
        editorPanel.repaint();
    }
}
