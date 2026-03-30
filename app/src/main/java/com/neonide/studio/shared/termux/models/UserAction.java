package com.neonide.studio.shared.termux.models;

public enum UserAction {

    CRASH_REPORT("crash report"),
    PLUGIN_EXECUTION_COMMAND("plugin execution command"),

    // Used when user chooses to file an issue from a terminal transcript
    REPORT_ISSUE_FROM_TRANSCRIPT("report issue from transcript");

    private final String name;

    UserAction(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
