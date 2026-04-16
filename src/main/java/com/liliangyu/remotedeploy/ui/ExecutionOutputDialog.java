package com.liliangyu.remotedeploy.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Font;

/** Displays captured stdout and stderr in a readable, copyable dialog after deployment. */
public final class ExecutionOutputDialog extends DialogWrapper {
    private final String output;

    public ExecutionOutputDialog(@Nullable Project project, String title, String output) {
        super(project);
        this.output = output;
        setTitle(title);
        setOKButtonText(RemoteDeployBundle.message("common.close"));
        setResizable(true);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBTextArea textArea = new JBTextArea(output);
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textArea.getFont().getSize()));

        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(JBUI.size(720, 420));
        return scrollPane;
    }
}
