package com.hristrix.chatrix.integration.tiktok;

import com.hristrix.chatrix.integration.chat.ChatAuthorRoles;
import com.hristrix.chatrix.integration.tiktok.proto.TikTokProto;
import com.hristrix.chatrix.messages.MessageEmote;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class TikTokStateCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void run() throws Exception {
        AtomicInteger confirmations = new AtomicInteger();
        java.util.ArrayList<String> comments = new java.util.ArrayList<>();
        var handler = new TikTokWebSocket.ChatHandler() {
            public void onConnected() { confirmations.incrementAndGet(); }
            public void onChat(long id, String user, String name, String text, ChatAuthorRoles roles, List<MessageEmote> emotes) { comments.add(text); }
            public void onGift(long id, String user, String name, String gift, int count) {}
            public void onFollow(long id, String user, String name) {}
            public void onSubscribe(long id, String user, String name) {}
            public void onLiveEnded() {}
        };
        TikTokWebSocket socket = new TikTokWebSocket("123", "", handler);
        var running = TikTokWebSocket.class.getDeclaredField("running");
        running.setAccessible(true);
        running.setBoolean(socket, true);
        var ctor = Class.forName(TikTokWebSocket.class.getName() + "$Listener").getDeclaredConstructor(TikTokWebSocket.class);
        ctor.setAccessible(true);
        WebSocket.Listener listener = (WebSocket.Listener) ctor.newInstance(socket);
        FakeSocket peer = new FakeSocket();
        listener.onOpen(peer);
        check(!socket.isConnected() && confirmations.get() == 0, "A WebSocket handshake alone confirmed TikTok chat");
        byte[] empty = TikTokProto.encode(w -> w.writeString(7, "msg"));
        listener.onBinary(peer, ByteBuffer.wrap(empty), true);
        check(!socket.isConnected(), "Empty feed confirmed TikTok chat");
        byte[] payload = TikTokProto.encode(w -> w.writeString(2, "cursor"));
        byte[] frame = TikTokProto.encode(w -> { w.writeString(7, "msg"); w.writeBytes(8, payload); });
        listener.onBinary(peer, ByteBuffer.wrap(frame), true);
        listener.onBinary(peer, ByteBuffer.wrap(frame), true);
        check(socket.isConnected() && confirmations.get() == 1, "Valid feed did not confirm exactly once");
        // Real LIVE comments carry a plain language string in field 14. Treating it
        // as a protobuf emote used to throw and silently discard every comment.
        for (String language : new String[] {"bg", "en", "ja"}) {
            byte[] chat = TikTokProto.encode(w -> { w.writeString(3, "Здравей 👋"); w.writeString(14, language); });
            deliverChat(listener, peer, chat);
        }
        byte[] malformedEmotes = TikTokProto.encode(w -> {
            w.writeString(3, "keep this comment");
            w.writeBytes(13, new byte[] {10, 127});
        });
        deliverChat(listener, peer, malformedEmotes);
        check(comments.equals(List.of("Здравей 👋", "Здравей 👋", "Здравей 👋", "keep this comment")),
            "Comment language or malformed optional emotes discarded chat text");
        byte[] details = TikTokProto.encode(w -> w.writeString(1, "sticker-id"));
        byte[] indexed = TikTokProto.encode(w -> { w.writeInt32(1, 0); w.writeBytes(2, details); });
        byte[] stickerChat = TikTokProto.encode(w -> { w.writeString(3, "[hi]"); w.writeBytes(13, indexed); w.writeString(14, "en"); });
        var parsedEmotes = TikTokEmoteParser.parseChatEmotes(TikTokProto.decode(stickerChat), "[hi]");
        check(parsedEmotes.size() == 1 && parsedEmotes.get(0).getStartIndex() == 0,
            "Valid inline TikTok sticker was lost");
        listener.onClose(peer, 1000, "ended");
        check(!socket.isConnected() && !socket.isRunning(), "Closed TikTok feed remained connected");
        socket.disconnect();
        TikTokWebSocket cancelled = new TikTokWebSocket("123", "", handler);
        cancelled.disconnect();
        WebSocket.Listener late = (WebSocket.Listener) ctor.newInstance(cancelled);
        FakeSocket latePeer = new FakeSocket();
        late.onOpen(latePeer);
        check(latePeer.aborted && !cancelled.isRunning(), "Cancelled connection reopened");
        TikTokWebSocket failed = new TikTokWebSocket("123", "", handler);
        running.setBoolean(failed, true);
        ((WebSocket.Listener) ctor.newInstance(failed)).onError(new FakeSocket(), new java.io.IOException("test failure"));
        check(!failed.isConnected() && !failed.isRunning() && failed.getFailure() != null, "Failed TikTok feed remained connected");
        System.out.println("TikTok handshake/feed confirmation, close, errors, and cancellation: PASS");
    }

    private static void deliverChat(WebSocket.Listener listener, WebSocket peer, byte[] chat) {
        byte[] message = TikTokProto.encode(w -> { w.writeString(1, "WebcastChatMessage"); w.writeBytes(2, chat); });
        byte[] response = TikTokProto.encode(w -> w.writeBytes(1, message));
        byte[] frame = TikTokProto.encode(w -> { w.writeString(7, "msg"); w.writeBytes(8, response); });
        listener.onBinary(peer, ByteBuffer.wrap(frame), true);
    }

    private static class FakeSocket implements WebSocket {
        boolean aborted;
        public CompletableFuture<WebSocket> sendText(CharSequence s, boolean last) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer b, boolean last) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendPing(ByteBuffer b) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendPong(ByteBuffer b) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendClose(int status, String reason) { return CompletableFuture.completedFuture(this); }
        public void request(long n) {}
        public String getSubprotocol() { return ""; }
        public boolean isOutputClosed() { return aborted; }
        public boolean isInputClosed() { return aborted; }
        public void abort() { aborted = true; }
    }
}
