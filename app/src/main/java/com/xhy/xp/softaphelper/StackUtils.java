package com.xhy.xp.softaphelper;

import android.util.Log;

public class StackUtils {
    public static String getStackTraceString() {
        return Log.getStackTraceString(new Throwable());
    }

    public static boolean isCallingFrom(String className, String methodName) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTraceElements) {
            if (element.getClassName().contains(className)
                    && element.getMethodName().contains(methodName)) {
                return true;
            }
        }
        return false;
    }
}
