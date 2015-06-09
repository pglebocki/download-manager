package com.novoda.downloadmanager.lib;

import android.content.Context;

class GlobalState {

    private static Context context;
    private static boolean verboseLogging;

    public static void setContext(Context context) {
        GlobalState.context = context;
    }

    public static Context getContext() {
        return context;
    }

    public static boolean hasVerboseLogging() {
        return verboseLogging;
    }

    public static void setVerboseLogging(boolean verboseLogging) {
        GlobalState.verboseLogging = verboseLogging;
    }
}
