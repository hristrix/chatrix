package com.hristrix.chatrix.interfaces;

/**
 * Interface for managing blocked words.
 */
public interface IBanwordManager {
    /**
     * Loads blocked words from a file.
     */
    void loadBanwords();
    
    /**
     * Checks whether a message contains blocked words.
     * @param message message to check
     * @return true if blocked words are present
     */
    boolean containsBanwords(String message);
    
    /**
     * Filters blocked words out of a message.
     * @param message original message
     * @return filtered message
     */
    String filterBanwords(String message);
    
    /**
     * Adds a blocked word.
     * @param banword blocked word to add
     */
    void addBanword(String banword);
    
    /**
     * Removes a blocked word.
     * @param banword blocked word to remove
     */
    void removeBanword(String banword);

    /**
     * Returns an immutable set of the current blocked words.
     */
    java.util.Set<String> getBanwords();
} 