package com.liliangyu.remotedeploy.ui;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import com.liliangyu.remotedeploy.model.ServerConfig;
import com.liliangyu.remotedeploy.service.RemoteDirectoryService;
import com.liliangyu.remotedeploy.service.RemotePathSupport;
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
import java.io.IOException;

/**
 * Lets users browse one remote server lazily and return either the highlighted child directory or the current directory.
 */
public final class RemoteDirectoryChooserDialog extends DialogWrapper {
    private final @Nullable Project project;
    private final ServerConfig server;
    private final RemoteDirectoryService remoteDirectoryService = new RemoteDirectoryService();
    private final JBTextField currentPathField = new JBTextField();
    private final JBList<RemoteDirectoryService.DirectoryEntry> directoryList = new JBList<>(new DefaultListModel<>());
    private final JButton upButton = new JButton();
    private final JButton refreshButton = new JButton();

    private String currentDirectory = "";
    private @Nullable String selectedDirectory;

    public RemoteDirectoryChooserDialog(@Nullable Project project, ServerConfig server, String initialDirectory) {
        super(project);
        this.project = project;
        this.server = server;

        setTitle(RemoteDeployBundle.message("remote.directory.browser.title", server));
        setOKButtonText(RemoteDeployBundle.message("common.select"));
        setResizable(true);

        currentPathField.setEditable(false);
        directoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        directoryList.setCellRenderer(new DirectoryEntryRenderer());
        directoryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedDirectory();
                }
            }
        });

        upButton.addActionListener(event -> loadDirectory(RemotePathSupport.parentOf(currentDirectory)));
        refreshButton.addActionListener(event -> loadDirectory(currentDirectory));

        init();
        refreshTexts();
        setOKActionEnabled(false);
        updateNavigationState();
        loadDirectory(initialDirectory);
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane listScrollPane = new JBScrollPane(directoryList);
        listScrollPane.setPreferredSize(JBUI.size(560, 300));

        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        buttonPanel.add(upButton);
        buttonPanel.add(refreshButton);

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(RemoteDeployBundle.message("remote.directory.browser.currentPath"), currentPathField)
                .getPanel(),
            BorderLayout.NORTH
        );
        centerPanel.add(listScrollPane, BorderLayout.CENTER);
        centerPanel.add(buttonPanel, BorderLayout.EAST);
        centerPanel.add(new JBLabel(RemoteDeployBundle.message("remote.directory.browser.hint")), BorderLayout.SOUTH);
        return centerPanel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return directoryList;
    }

    @Override
    protected void doOKAction() {
        selectedDirectory = getSelectedDirectoryPath();
        super.doOKAction();
    }

    public String getSelectedDirectory() {
        return selectedDirectory == null || selectedDirectory.isBlank() ? currentDirectory : selectedDirectory;
    }

    /**
     * Loads exactly one directory level with modal progress so slow remote servers do not freeze the dialog.
     */
    private void loadDirectory(String requestedDirectory) {
        try {
            RemoteDirectoryService.DirectorySnapshot snapshot = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                (ThrowableComputable<RemoteDirectoryService.DirectorySnapshot, IOException>) () -> {
                    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                    return remoteDirectoryService.loadDirectory(
                        server,
                        requestedDirectory,
                        indicator == null ? new EmptyProgressIndicator() : indicator
                    );
                },
                RemoteDeployBundle.message("remote.directory.browser.progress"),
                true,
                project
            );
            applySnapshot(snapshot);
        } catch (ProcessCanceledException ignored) {
            // User canceled the progress dialog and should stay on the last successfully loaded directory.
        } catch (IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), RemoteDeployBundle.message("remote.directory.browser.loadFailure"));
        }
    }

    /**
     * Refreshes the visible list while leaving selection empty so OK defaults to the current directory unless the user picks a child.
     */
    private void applySnapshot(RemoteDirectoryService.DirectorySnapshot snapshot) {
        currentDirectory = snapshot.currentDirectory();
        currentPathField.setText(currentDirectory);

        DefaultListModel<RemoteDirectoryService.DirectoryEntry> model = (DefaultListModel<RemoteDirectoryService.DirectoryEntry>) directoryList.getModel();
        model.clear();
        for (RemoteDirectoryService.DirectoryEntry directory : snapshot.directories()) {
            model.addElement(directory);
        }
        directoryList.clearSelection();
        directoryList.getEmptyText().setText(RemoteDeployBundle.message("remote.directory.browser.empty"));
        updateNavigationState();
        setOKActionEnabled(!currentDirectory.isBlank());
    }

    private void updateNavigationState() {
        upButton.setEnabled(!currentDirectory.isBlank() && !"/".equals(currentDirectory));
        refreshButton.setEnabled(!currentDirectory.isBlank());
    }

    private void openSelectedDirectory() {
        RemoteDirectoryService.DirectoryEntry selectedEntry = directoryList.getSelectedValue();
        if (selectedEntry == null) {
            return;
        }
        loadDirectory(selectedEntry.path());
    }

    private String getSelectedDirectoryPath() {
        RemoteDirectoryService.DirectoryEntry selectedEntry = directoryList.getSelectedValue();
        return selectedEntry == null ? currentDirectory : selectedEntry.path();
    }

    private void refreshTexts() {
        setTitle(RemoteDeployBundle.message("remote.directory.browser.title", server));
        setOKButtonText(RemoteDeployBundle.message("common.select"));
        upButton.setText(RemoteDeployBundle.message("common.up"));
        refreshButton.setText(RemoteDeployBundle.message("common.refresh"));
        directoryList.getEmptyText().setText(RemoteDeployBundle.message("remote.directory.browser.empty"));
    }

    private static final class DirectoryEntryRenderer extends ColoredListCellRenderer<RemoteDirectoryService.DirectoryEntry> {
        @Override
        protected void customizeCellRenderer(
            javax.swing.JList<? extends RemoteDirectoryService.DirectoryEntry> list,
            RemoteDirectoryService.DirectoryEntry value,
            int index,
            boolean selected,
            boolean hasFocus
        ) {
            if (value == null) {
                return;
            }
            append(value.name());
            append("  " + value.path(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
