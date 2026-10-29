package com.rtpduels.plugin.compat;

import java.lang.reflect.Method;

/**
 * Tiny reflection helpers. Everything version-sensitive in RTP Duels goes through
 * reflection so ONE jar can load on Minecraft 1.8 through the newest release without
 * ever failing class verification or linking on a method that doesn't exist there.
 */
public final class Reflect {

    private Reflect() {}

    /** Public method lookup that returns null instead of throwing. */
    public static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Class lookup that returns null instead of throwing. */
    public static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Invoke and swallow everything - returns null on any failure. */
    public static Object invokeQuietly(Method method, Object target, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Reads a public static field (works for enum constants AND interface constants such as the modern Sound). */
    public static Object staticField(Class<?> type, String name) {
        try {
            return type.getField(name).get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
