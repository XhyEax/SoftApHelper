package com.xhy.xp.softaphelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectUtils {
    public static Field findField(Class<?> klass, String fieldName) {
        for (Field f : klass.getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }
        return null;
    }

    public static Method findMethod(Class<?> klass, String methodName) {
        for (Method m : klass.getDeclaredMethods()) {
            m.setAccessible(true);
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }
}
