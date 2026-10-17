package com.hristrix.chatrix.integration.chat;
public class ChatMessagePipeline {
 public final java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
 public volatile IncomingChatMessage last;
 public void process(IncomingChatMessage message) { last=message; count.incrementAndGet(); }
 public void processYouTubeBatch(java.util.List<IncomingChatMessage> messages) { messages.forEach(this::process); }
}
