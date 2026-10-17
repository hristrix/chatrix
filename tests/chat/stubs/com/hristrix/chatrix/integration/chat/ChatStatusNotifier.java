package com.hristrix.chatrix.integration.chat;
public class ChatStatusNotifier {
 public static void showTranslatableSuccess(String key,Object...args) { System.out.println("Confirmed subscription: "+java.util.Arrays.toString(args)); }
}
