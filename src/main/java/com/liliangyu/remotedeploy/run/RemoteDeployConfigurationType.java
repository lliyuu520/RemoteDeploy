package com.liliangyu.remotedeploy.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.liliangyu.remotedeploy.i18n.RemoteDeployBundle;

/**
 * Registers the Remote Deploy run configuration and exposes multiple preset factories in Run/Debug UI.
 */
public final class RemoteDeployConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "RemoteDeployConfigurationType";

    public RemoteDeployConfigurationType() {
        super(
            ID,
            RemoteDeployBundle.message("run.config.type.displayName"),
            RemoteDeployBundle.message("run.config.type.description"),
            AllIcons.Actions.Upload
        );

        addFactory(new RemoteDeployConfigurationFactory(
            this,
            "run.config.factory.default",
            "$ProjectFileDir$",
            "",
            ""
        ));
        addFactory(new RemoteDeployConfigurationFactory(
            this,
            "run.config.factory.uploadOnly",
            "$ProjectFileDir$",
            "",
            ""
        ));
        addFactory(new RemoteDeployConfigurationFactory(
            this,
            "run.config.factory.deployScript",
            "$ProjectFileDir$",
            "",
            "bash deploy.sh"
        ));
    }

    @Override
    public String getDisplayName() {
        return RemoteDeployBundle.message("run.config.type.displayName");
    }

    private static final class RemoteDeployConfigurationFactory extends ConfigurationFactory {
        private final String displayNameKey;
        private final String defaultLocalPath;
        private final String defaultRemoteDirectory;
        private final String defaultCommand;

        private RemoteDeployConfigurationFactory(
            RemoteDeployConfigurationType type,
            String displayNameKey,
            String defaultLocalPath,
            String defaultRemoteDirectory,
            String defaultCommand
        ) {
            super(type);
            this.displayNameKey = displayNameKey;
            this.defaultLocalPath = defaultLocalPath;
            this.defaultRemoteDirectory = defaultRemoteDirectory;
            this.defaultCommand = defaultCommand;
        }

        @Override
        public String getName() {
            return RemoteDeployBundle.message(displayNameKey);
        }

        @Override
        public RemoteDeployRunConfiguration createTemplateConfiguration(Project project) {
            String displayName = getName();
            RemoteDeployRunConfiguration configuration = new RemoteDeployRunConfiguration(project, this, displayName);
            configuration.setLocalPath(defaultLocalPath);
            configuration.setRemoteDirectory(defaultRemoteDirectory);
            configuration.setCommand(defaultCommand);
            return configuration;
        }
    }
}
