package me.lubomirstankov.gotcraftproxychat.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple Dependency Injection Container for managing service instances
 */
public class DIContainer {

    private static final Map<Class<?>, Object> services = new HashMap<>();

    /**
     * Register a service instance
     * @param clazz The service class
     * @param instance The service instance
     * @param <T> The type of the service
     */
    public static <T> void register(Class<T> clazz, T instance) {
        services.put(clazz, instance);
    }

    /**
     * Get a service instance
     * @param clazz The service class
     * @param <T> The type of the service
     * @return The service instance, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz) {
        return (T) services.get(clazz);
    }

    /**
     * Check if a service is registered
     * @param clazz The service class
     * @return true if registered, false otherwise
     */
    public static boolean has(Class<?> clazz) {
        return services.containsKey(clazz);
    }

    /**
     * Clear all registered services
     */
    public static void clear() {
        services.clear();
    }
}

