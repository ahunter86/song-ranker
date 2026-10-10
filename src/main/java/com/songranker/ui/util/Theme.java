package com.songranker.ui.util;

public enum Theme {
    DARK("/theme-dark.css"),
    LIGHT("/theme-light.css");

    public final String stylesheetPath;

    Theme(String stylesheetPath) {
        this.stylesheetPath = stylesheetPath;
    }

    public Theme other() {
        return this == DARK ? LIGHT : DARK;
    }
}
