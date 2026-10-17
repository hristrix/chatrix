package com.hristrix.chatrix.utils;

import net.minecraft.client.MinecraftClient;
import java.util.logging.Level;

/**
 * Utilities for logging and sending messages to the player.
 */
public class Logger {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("Chatrix");
    
    /**
     * Logs an informational message.
     * @param message message to log
     */
    public static void info(String message) {
        LOGGER.info(message);
    }
    
    /**
     * Logs a warning.
     * @param message message to log
     */
    public static void warning(String message) {
        LOGGER.warning(message);
    }
    
    /**
     * Logs an error.
     * @param message message to log
     */
    public static void error(String message) {
        LOGGER.log(Level.SEVERE, message);
    }
    
    /**
     * Logs an error with an exception.
     * @param message message to log
     * @param throwable exception
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }
    
    /**
     * Sends a message to the player.
     * @param message message to send
     */
    public static void sendToPlayer(String message) {
        PlayerMessageCompat.send(MinecraftClient.getInstance(), message);
    }
    
    /**
     * Sends an informational message to the player.
     * @param message message to send
     */
    public static void sendInfoToPlayer(String message) {
        sendToPlayer("§a" + message);
    }
    
    /**
     * Sends a warning to the player.
     * @param message message to send
     */
    public static void sendWarningToPlayer(String message) {
        sendToPlayer("§e" + message);
    }
    
    /**
     * Sends an error to the player.
     * @param message message to send
     */
    public static void sendErrorToPlayer(String message) {
        sendToPlayer("§c" + message);
    }
} 