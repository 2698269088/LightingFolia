package top.mcocet.lightingFolia.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for reflection operations with MethodHandle caching.
 * Provides high-performance access to Folia internals and Bukkit APIs.
 */
public class ReflectionUtil {

    private static final Logger LOGGER = Logger.getLogger("LightingFolia-Reflection");
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Get a class by name, returning null if not found.
     */
    public static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Check if a class exists.
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get a method handle for a static method.
     */
    public static MethodHandle getStaticMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            MethodType methodType = MethodType.methodType(getReturnType(clazz, methodName, paramTypes), paramTypes);
            return LOOKUP.findStatic(clazz, methodName, methodType);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get static method " + methodName + " from " + clazz.getName(), e);
            return null;
        }
    }

    /**
     * Get a method handle for a virtual method.
     */
    public static MethodHandle getVirtualMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            MethodType methodType = MethodType.methodType(getReturnType(clazz, methodName, paramTypes), paramTypes);
            return LOOKUP.findVirtual(clazz, methodName, methodType);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get virtual method " + methodName + " from " + clazz.getName(), e);
            return null;
        }
    }

    /**
     * Get a field handle.
     */
    public static MethodHandle getFieldGetter(Class<?> clazz, String fieldName) {
        try {
            return LOOKUP.findGetter(clazz, fieldName, getFieldType(clazz, fieldName));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get field getter for " + fieldName + " from " + clazz.getName(), e);
            return null;
        }
    }

    /**
     * Get a field handle for setting.
     */
    public static MethodHandle getFieldSetter(Class<?> clazz, String fieldName) {
        try {
            return LOOKUP.findSetter(clazz, fieldName, getFieldType(clazz, fieldName));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get field setter for " + fieldName + " from " + clazz.getName(), e);
            return null;
        }
    }

    /**
     * Invoke a method handle safely.
     */
    public static Object invoke(MethodHandle handle, Object... args) {
        if (handle == null) {
            return null;
        }
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to invoke method handle", e);
            return null;
        }
    }

    /**
     * Get a method via reflection.
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.FINE, "Method not found: " + methodName + " in " + clazz.getName());
            return null;
        }
    }

    /**
     * Get a declared method via reflection.
     */
    public static Method getDeclaredMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.FINE, "Declared method not found: " + methodName + " in " + clazz.getName());
            return null;
        }
    }

    /**
     * Get a field via reflection.
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            LOGGER.log(Level.FINE, "Field not found: " + fieldName + " in " + clazz.getName());
            return null;
        }
    }

    /**
     * Get a declared field via reflection.
     */
    public static Field getDeclaredField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            LOGGER.log(Level.FINE, "Declared field not found: " + fieldName + " in " + clazz.getName());
            return null;
        }
    }

    /**
     * Get field value.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String fieldName) {
        try {
            Field field = getDeclaredField(obj.getClass(), fieldName);
            if (field != null) {
                return (T) field.get(obj);
            }
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.WARNING, "Failed to get field value: " + fieldName, e);
        }
        return null;
    }

    /**
     * Set field value.
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) {
        try {
            Field field = getDeclaredField(obj.getClass(), fieldName);
            if (field != null) {
                field.set(obj, value);
            }
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.WARNING, "Failed to set field value: " + fieldName, e);
        }
    }

    /**
     * Invoke a method reflectively.
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object obj, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
            Method method = getDeclaredMethod(obj.getClass(), methodName, paramTypes);
            if (method != null) {
                return (T) method.invoke(obj, args);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invoke method: " + methodName, e);
        }
        return null;
    }

    /**
     * Invoke a static method reflectively.
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
            Method method = getDeclaredMethod(clazz, methodName, paramTypes);
            if (method != null) {
                return (T) method.invoke(null, args);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invoke static method: " + methodName, e);
        }
        return null;
    }

    // Helper methods

    private static Class<?> getReturnType(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            return method.getReturnType();
        } catch (NoSuchMethodException e) {
            return Object.class;
        }
    }

    private static Class<?> getFieldType(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getField(fieldName);
            return field.getType();
        } catch (NoSuchFieldException e) {
            return Object.class;
        }
    }
}
