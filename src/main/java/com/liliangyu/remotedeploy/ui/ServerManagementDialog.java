package com.liliangyu.remotedeploy.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Centralizes server CRUD so deploy flows and run configurations only need to choose an existing target.
 */
public final class ServerManagementDialog extends DialogWrapper {
    private final @Nullable Project project;
    private final RemoteDeploySettingsService settingsService = RemoteDeploySettingsService.getInstance();
    private final JBList<ServerConfig> serverList = new JBList<>(new DefaultListModel<>());
    private final JButton addButton = new JButton();
    private final JButton editButton = new JButton();
    private final JButton removeButton = new JButton();

    private @Nullable String selectedServerId;

    public ServerManagementDialog(@Nullable Project project, @Nullable String preferredServerId) {
        super(project);
        this.project = project;
        this.selectedServerId = preferredServerId;

        setTitle(RemoteDeployBundle.message("server.manager.title"));
        setOKButtonText(RemoteDeployBundle.message("common.close"));
        setResizable(true);

        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.getEmptyText().setText(RemoteDeployBundle.message("server.manager.empty"));
        serverList.setCellRenderer(new ServerListRenderer());
        serverList.addListSelectionListener(event -> updateSelectionState());
        serverList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && getSelectedServer() != null) {
                    editSelectedServer();
                }
            }
        });

        addButton.addActionListener(event -> addServer());
        editButton.addActionListener(event -> editSelectedServer());
        removeButton.addActionListener(event -> removeSelectedServer());

        init();
        refreshTexts();
        reloadServers(preferredServerId);
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane listScrollPane = new JBScrollPane(serverList);
        listScrollPane.setPreferredSize(JBUI.size(520, 280));

        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        JPanel contentPanel = new JPanel(new BorderLayout(8, 0));
        contentPanel.add(listScrollPane, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JBLabel(RemoteDeployBundle.message("server.manager.sharedHint")), BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return serverList;
    }

    @Override
    protected void doOKAction() {
        selectedServerId = getSelectedServerId();
        super.doOKAction();
    }

    public @Nullable String getSelectedServerId() {
        ServerConfig selectedServer = getSelectedServer();
        return selectedServer == null ? selectedServerId : selectedServer.getId();
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
            RemoteDeployBundle.message("server.manager.removeServer.message", selectedServer.getName()),
            RemoteDeployBundle.message("server.manager.removeServer.title"),
            Messages.getQuestionIcon()
        );
        if (answer != Messages.YES) {
            return;
        }

        settingsService.deleteServer(selectedServer.getId());
        reloadServers(null);
    }

    /**
     * Routes all server persistence through the settings service so dialogs no longer duplicate secret handling.
     */
    private void persistServer(ServerConfigDialog.Result result) {
        settingsService.saveServerWithSecrets(result.server(), result.password(), result.passphrase());
    }

    /**
     * Rebuilds the list from detached server copies and keeps the last meaningful selection when possible.
     */
    private void reloadServers(@Nullable String preferredServerId) {
        DefaultListModel<ServerConfig> model = (DefaultListModel<ServerConfig>) serverList.getModel();
        model.clear();

        List<ServerConfig> servers = settingsService.getServers();
        for (ServerConfig server : servers) {
            model.addElement(server);
        }

        selectServer(preferredServerId, servers);
        updateSelectionState();
    }

    private void selectServer(@Nullable String preferredServerId, List<ServerConfig> servers) {
        if (servers.isEmpty()) {
            selectedServerId = null;
            serverList.clearSelection();
            return;
        }

        if (preferredServerId != null) {
            for (int index = 0; index < servers.size(); index++) {
                if (preferredServerId.equals(servers.get(index).getId())) {
                    serverList.setSelectedIndex(index);
                    selectedServerId = preferredServerId;
                    return;
                }
            }
        }

        serverList.setSelectedIndex(0);
        selectedServerId = servers.getFirst().getId();
    }

    private void updateSelectionState() {
        ServerConfig selectedServer = getSelectedServer();
        editButton.setEnabled(selectedServer != null);
        removeButton.setEnabled(selectedServer != null);
        selectedServerId = selectedServer == null ? selectedServerId : selectedServer.getId();
    }

    private @Nullable ServerConfig getSelectedServer() {
        return serverList.getSelectedValue();
    }

    private void refreshTexts() {
        setTitle(RemoteDeployBundle.message("server.manager.title"));
        setOKButtonText(RemoteDeployBundle.message("common.close"));
        addButton.setText(RemoteDeployBundle.message("common.add"));
        editButton.setText(RemoteDeployBundle.message("common.edit"));
        removeButton.setText(RemoteDeployBundle.message("common.remove"));
    }

    private static final class ServerListRenderer extends ColoredListCellRenderer<ServerConfig> {
        @Override
        protected void customizeCellRenderer(
            javax.swing.JList<? extends ServerConfig> list,
            ServerConfig value,
            int index,
            boolean selected,
            boolean hasFocus
        ) {
            if (value == null) {
                return;
            }

            String primaryText = value.getName() == null || value.getName().isBlank() ? value.getHost() : value.getName();
            append(primaryText);

            String host = value.getHost() == null ? "" : value.getHost().trim();
            String username = value.getUsername() == null ? "" : value.getUsername().trim();
            String secondaryText = username.isEmpty() ? host : username + "@" + host;
            if (!secondaryText.isEmpty()) {
                append("  " + secondaryText + ":" + value.getPort(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}
