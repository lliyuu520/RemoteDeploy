package com.liliangyu.remotedeploy.i18n;

import com.intellij.DynamicBundle;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves plugin messages from the current IDEA UI locale while intentionally collapsing every non-Chinese language to English.
 */
public final class RemoteDeployBundle {
    private static final String BUNDLE_NAME = "messages.RemoteDeployBundle";
    private static final Locale ENGLISH_LOCALE = Locale.ENGLISH;
    private static final Locale CHINESE_LOCALE = Locale.SIMPLIFIED_CHINESE;
    private static final Map<Locale, ResourceBundle> BUNDLES = new ConcurrentHashMap<>();

    private RemoteDeployBundle() {
    }

    public static String message(String key, Object... args) {
        Locale locale = currentLocale();
        ResourceBundle bundle = BUNDLES.computeIfAbsent(locale, candidate -> ResourceBundle.getBundle(BUNDLE_NAME, candidate));
        String pattern = bundle.getString(key);
        if (args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, bundle.getLocale()).format(args);
    }

    /**
     * Keeps the plugin bilingual by treating any Chinese IDEA locale as Simplified Chinese and every other locale as English.
     */
    public static Locale currentLocale() {
        Locale ideLocale = DynamicBundle.getLocale();
        return "zh".equalsIgnoreCase(ideLocale.getLanguage()) ? CHINESE_LOCALE : ENGLISH_LOCALE;
    }
}
