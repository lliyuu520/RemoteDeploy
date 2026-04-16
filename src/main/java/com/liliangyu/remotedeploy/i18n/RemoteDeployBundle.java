package com.liliangyu.remotedeploy.i18n;

import com.intellij.openapi.application.ApplicationManager;
import com.liliangyu.remotedeploy.settings.RemoteDeploySettingsService;

import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves plugin messages from the persisted UI language so dialogs, notifications, and run output stay aligned.
 */
public final class RemoteDeployBundle {
    private static final String BUNDLE_NAME = "messages.RemoteDeployBundle";
    private static final Map<UiLanguage, ResourceBundle> BUNDLES = new ConcurrentHashMap<>();

    private RemoteDeployBundle() {
    }

    public static String message(String key, Object... args) {
        ResourceBundle bundle = BUNDLES.computeIfAbsent(currentLanguage(),
            language -> ResourceBundle.getBundle(BUNDLE_NAME, language.locale()));
        String pattern = bundle.getString(key);
        if (args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, bundle.getLocale()).format(args);
    }

    /**
     * Falls back to English when the application service is not available yet, such as during early action creation.
     */
    public static UiLanguage currentLanguage() {
        var application = ApplicationManager.getApplication();
        if (application == null) {
            return UiLanguage.ENGLISH;
        }
        RemoteDeploySettingsService service = application.getService(RemoteDeploySettingsService.class);
        return service == null ? UiLanguage.ENGLISH : service.getUiLanguage();
    }
}
