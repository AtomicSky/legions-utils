package com.legions.client;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared, classloader-safe cache for optional integration fields. */
public final class LegionsReflection {
    private static final ClassValue<ConcurrentMap<String, Field>> FIELDS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<String, Field> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private LegionsReflection() {
    }

    public static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        ConcurrentMap<String, Field> fields = FIELDS.get(owner);
        Field cached = fields.get(name);
        if (cached != null) {
            return cached;
        }
        Field discovered = owner.getDeclaredField(name);
        discovered.setAccessible(true);
        Field existing = fields.putIfAbsent(name, discovered);
        return existing == null ? discovered : existing;
    }

    public static Object getStatic(Class<?> owner, String name) throws ReflectiveOperationException {
        return field(owner, name).get(null);
    }

    public static Object get(Object owner, String name) throws ReflectiveOperationException {
        return field(owner.getClass(), name).get(owner);
    }

    public static boolean getBoolean(Object owner, String name) throws ReflectiveOperationException {
        Object value = get(owner, name);
        return value instanceof Boolean bool && bool;
    }

    public static String getString(Object owner, String name) throws ReflectiveOperationException {
        Object value = get(owner, name);
        return value instanceof String text ? text : "";
    }

    public static void set(Object owner, String name, Object value) throws ReflectiveOperationException {
        field(owner.getClass(), name).set(owner, value);
    }
}
