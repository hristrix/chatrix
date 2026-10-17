package com.hristrix.chatrix.interfaces;

/**
 * Interface for managing mod configuration.
 */
public interface IConfigManager {
    /**
     * Loads configuration from a file.
     */
    void loadConfig();
    
    /**
     * Saves configuration to a file.
     */
    void saveConfig();
    
    /**
     * Gets a configuration value by key.
     * @param key configuration key
     * @return configuration value
     */
    Object getConfigValue(String key);
    
    /**
     * Sets a configuration value.
     * @param key configuration key
     * @param value new value
     */
    void setConfigValue(String key, Object value);
} 