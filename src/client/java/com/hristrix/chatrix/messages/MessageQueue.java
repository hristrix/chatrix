package com.hristrix.chatrix.messages;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Queue of messages awaiting world or HUD display.
 * The queue is bounded; overflow discards messages with the latest scheduled display time.
 * Supports delayed display ({@code readyAtTick}) for timestamped YouTube batches.
 */
public class MessageQueue {
    public static class QueuedMessage {
        public final String text;
        public final Integer authorColorRgb;
        public final java.util.List<MessageEmote> emotes;
        /** First tick at which the message may be displayed. */
        public final int readyAtTick;
        /** Do not wait for textures beyond this tick; an unavailable CDN must not stall chat. */
        public final int textureDeadlineTick;
        final long sequence;

        public QueuedMessage(
            String text,
            Integer authorColorRgb,
            java.util.List<MessageEmote> emotes,
            int readyAtTick,
            int textureDeadlineTick,
            long sequence
        ) {
            this.text = text;
            this.authorColorRgb = authorColorRgb;
            this.emotes = emotes == null ? java.util.Collections.emptyList() : java.util.List.copyOf(emotes);
            this.readyAtTick = readyAtTick;
            this.textureDeadlineTick = textureDeadlineTick;
            this.sequence = sequence;
        }
    }

    private static final Comparator<QueuedMessage> ORDER = Comparator
        .comparingInt((QueuedMessage m) -> m.readyAtTick)
        .thenComparingLong(m -> m.sequence);

    /** Maximum queued messages; overflow discards messages with the latest scheduled display time. */
    private static final int MAX_QUEUE_SIZE = 16;

    private final PriorityBlockingQueue<QueuedMessage> messageQueue = new PriorityBlockingQueue<>(11, ORDER);
    private final AtomicLong sequence = new AtomicLong();

    public void enqueueMessage(
        String message,
        Integer authorColorRgb,
        java.util.List<MessageEmote> emotes,
        int readyAtTick,
        int textureDeadlineTick
    ) {
        offerWithCap(new QueuedMessage(
            message,
            authorColorRgb,
            emotes,
            readyAtTick,
            textureDeadlineTick,
            sequence.getAndIncrement()
        ));
    }

    private void offerWithCap(QueuedMessage message) {
        messageQueue.offer(message);
        trimToMaxSize();
    }

    private void trimToMaxSize() {
        while (messageQueue.size() > MAX_QUEUE_SIZE) {
            QueuedMessage toDrop = messageQueue.stream()
                .max(Comparator.comparingInt((QueuedMessage m) -> m.readyAtTick).thenComparingLong(m -> m.sequence))
                .orElse(null);
            if (toDrop == null) {
                break;
            }
            messageQueue.remove(toDrop);
        }
    }

    public QueuedMessage pollReady(int currentTick) {
        QueuedMessage head = messageQueue.peek();
        if (head == null || head.readyAtTick > currentTick) {
            return null;
        }
        if (currentTick >= head.textureDeadlineTick || TwitchEmoteTextureCache.areEmotesReady(head.emotes)) {
            return messageQueue.poll();
        }
        return null;
    }

    public void clear() {
        messageQueue.clear();
    }

    public boolean isEmpty() {
        return messageQueue.isEmpty();
    }

    public int pendingCount() {
        return messageQueue.size();
    }
}
