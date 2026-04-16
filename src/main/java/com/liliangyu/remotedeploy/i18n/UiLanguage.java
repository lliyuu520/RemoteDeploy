package com.liliangyu.remotedeploy.i18n;

import java.util.Locale;

/**
 * Enumerates the user-selectable UI languages the plugin can render with at runtime.
 */
public enum UiLanguage {
    ENGLISH("en", Locale.ENGLISH, "English"),
    SIMPLIFIED_CHINESE("zh", Locale.SIMPLIFIED_CHINESE, "中文");

    private final String id;
    private final Locale locale;
    private final String displayName;

    UiLanguage(String id, Locale locale, String displayName) {
        this.id = id;
        this.locale = locale;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public Locale locale() {
        return locale;
    }

    public static UiLanguage fromId(String id) {
        for (UiLanguage language : values()) {
            if (language.id.equalsIgnoreCase(id == null ? "" : id.trim())) {
                return language;
            }
        }
        return ENGLISH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
