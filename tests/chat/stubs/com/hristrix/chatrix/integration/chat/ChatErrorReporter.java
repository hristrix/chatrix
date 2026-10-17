package com.hristrix.chatrix.integration.chat;
public class ChatErrorReporter {
 public static void report(ChatPlatform platform,Exception e) { System.out.println(platform+" error: "+e); }
 public static void clear(ChatPlatform platform) {}
}
