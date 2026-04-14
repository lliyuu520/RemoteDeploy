package com.liliangyu.remotedeploy.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;

/**
 * Registers the Remote Deploy run configuration and exposes multiple preset factories in Run/Debug UI.
 */
public final class RemoteDeployConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "RemoteDeployConfigurationType";

    public RemoteDeployConfigurationType() {
        super(
            ID,
            "Remote Deploy",
            "Upload local files to a remote server and run a deployment command.",
            AllIcons.Actions.Upload
        );

        addFactory(new RemoteDeployConfigurationFactory(
            this,
            "Remote Deploy",
            "$ProjectFileDir$",
            "",
            ""
        ));
        addFactory(new RemoteDeployConfigurationFactory(
            this,
            "Remote Deploy (Upload Only)",
            "$ProjectFileDir$",
            "",
            ""
        ));
        addFactory(new RemoteDeployConfigurationFactory(
            this,
            "Remote Deploy (Deploy Script)",
            "$ProjectFileDir$",
            "",
            "bash deploy.sh"
        ));
    }

    private static final class RemoteDeployConfigurationFactory extends ConfigurationFactory {
        private final String displayName;
        private final String defaultLocalPath;
        private final String defaultRemoteDirectory;
        private final String defaultCommand;

        private RemoteDeployConfigurationFactory(
            RemoteDeployConfigurationType type,
            String displayName,
            String defaultLocalPath,
            String defaultRemoteDirectory,
            String defaultCommand
        ) {
            super(type);
            this.displayName = displayName;
            this.defaultLocalPath = defaultLocalPath;
            this.defaultRemoteDirectory = defaultRemoteDirectory;
            this.defaultCommand = defaultCommand;
        }

        @Override
        public String getName() {
            return displayName;
        }

        @Override
        public RemoteDeployRunConfiguration createTemplateConfiguration(Project project) {
            RemoteDeployRunConfiguration configuration = new RemoteDeployRunConfiguration(project, this, displayName);
            configuration.setLocalPath(defaultLocalPath);
            configuration.setRemoteDirectory(defaultRemoteDirectory);
            configuration.setCommand(defaultCommand);
            return configuration;
        }
    }
}
