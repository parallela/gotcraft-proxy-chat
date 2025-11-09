package me.lubomirstankov.gotcraftproxychat.common.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages configuration loading and access
 */
public class ConfigManager {

    private Map<String, Object> config;
    private final Path configPath;

    public ConfigManager(Path configPath) {
        this.configPath = configPath;
        this.config = new HashMap<>();
    }

    /**
     * Load configuration from file, creating default if it doesn't exist
     * @param defaultConfigStream Default configuration input stream
     */
    public void load(InputStream defaultConfigStream) {
        try {
            // Create config file if it doesn't exist
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                Files.copy(defaultConfigStream, configPath);
            }

            // Load configuration
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(configPath)) {
                config = yaml.load(is);
                if (config == null) {
                    config = new HashMap<>();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Reload configuration from file
     */
    public void reload() {
        try {
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(configPath)) {
                config = yaml.load(is);
                if (config == null) {
                    config = new HashMap<>();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload configuration", e);
        }
    }

    /**
     * Get a configuration value by path (e.g., "chat.enabled")
     * @param path The configuration path
     * @return The value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public Object get(String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = config;

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return null;
            }
        }

        return current.get(parts[parts.length - 1]);
    }

    /**
     * Get a string value
     * @param path The configuration path
     * @param defaultValue Default value if not found
     * @return The string value
     */
    public String getString(String path, String defaultValue) {
        Object value = get(path);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Get a boolean value
     * @param path The configuration path
     * @param defaultValue Default value if not found
     * @return The boolean value
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        Object value = get(path);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    /**
     * Get a map value
     * @param path The configuration path
     * @return The map value, or empty map if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String path) {
        Object value = get(path);
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }

    /**
     * Get the entire configuration
     * @return The configuration map
     */
    public Map<String, Object> getConfig() {
        return config;
    }
}

