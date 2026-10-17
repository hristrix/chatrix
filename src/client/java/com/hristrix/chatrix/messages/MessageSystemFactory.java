package com.hristrix.chatrix.messages;

/**
 * Factory for creating and initializing the message system.
 */
public class MessageSystemFactory {
    
    /**
     * Creates and initializes all message system components.
     * @return configured MessageSpawner
     */
    public static MessageSpawner createMessageSystem() {
        MessageQueue messageQueue = new MessageQueue();
        MessageParticleManager particleManager = new MessageParticleManager();
        MessageLifecycleManager lifecycleManager = new MessageLifecycleManager(particleManager);
        MessageSpawner messageSpawner = new MessageSpawner(messageQueue, lifecycleManager);
        new MessageRenderer(lifecycleManager, particleManager);
        new MessageHudRenderer(lifecycleManager);
        return messageSpawner;
    }
} 