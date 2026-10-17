import com.hristrix.chatrix.integration.chat.*;
import com.hristrix.chatrix.integration.twitch.TwitchChatClient;
import com.hristrix.chatrix.integration.kick.KickChatClient;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class ChatCheck {
 static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
 static void waitFor(java.util.function.BooleanSupplier condition,int millis) throws Exception {
  long end=System.nanoTime()+millis*1_000_000L;
  while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(20);
  check(condition.getAsBoolean(),"Timed out waiting for expected connection state");
 }
 public static void main(String[] args)throws Exception {
  check(ChatChannelName.normalize(" @Aethelthryth ","twitch").equals("aethelthryth"),"@username");
  check(ChatChannelName.normalize("https://www.twitch.tv/aethelthryth?ref=test","twitch").equals("aethelthryth"),"Twitch URL");
  check(ChatChannelName.normalize("kick.com/XQC/","kick").equals("xqc"),"Kick URL");
  for(String bad:new String[]{"https://www.youtube.com/@Aethelthryth1337","foo\r\nJOIN #other","", "user name"}) {
   boolean rejected=false;
   try {ChatChannelName.normalize(bad,"twitch");}catch(IllegalArgumentException e){rejected=true;}
   check(rejected,"Invalid channel accepted");
  }
  System.out.println("Username normalization and wrong-platform/IRC-injection rejection: PASS");
  try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
   int port=server.getLocalPort(); server.close();
   var socket=new SimpleWebSocket("ws://127.0.0.1:"+port,null,m->{});
   socket.connect();waitFor(()->!socket.isRunning(),5000);
   check(socket.getFailure()!=null&&!socket.isConnected(),"Handshake failure remained connected");
  }
  System.out.println("Failed WebSocket handshake clears running state: PASS");
  try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
   Thread peer=new Thread(()->{
    try(var socket=server.accept()) {
     var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
     String key=null,line;
     while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase().startsWith("sec-websocket-key:"))key=line.substring(line.indexOf(':')+1).trim();
     String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
     var out=socket.getOutputStream();
     out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
     out.write(new byte[]{(byte)0x88,2,3,(byte)0xe8});out.flush();Thread.sleep(200);
    }catch(Exception e){throw new RuntimeException(e);}
   });peer.start();
   var socket=new SimpleWebSocket("ws://127.0.0.1:"+server.getLocalPort(),null,m->{});
   socket.connect();waitFor(()->!socket.isRunning(),5000);
   check(!socket.isConnected(),"Remote close remained connected");peer.join();
  }
  System.out.println("Remote WebSocket close clears running state: PASS");
  var validate=Class.forName("com.hristrix.chatrix.integration.youtube.YouTubeChatClient").getDeclaredMethod("validatedContinuation",com.google.gson.JsonObject.class);
  validate.setAccessible(true);
  for(String type:new String[]{"timedContinuationData","invalidationContinuationData","reloadContinuationData"}) {
   var response=com.google.gson.JsonParser.parseString("{\"continuationContents\":{\"liveChatContinuation\":{\"continuations\":[{\""+type+"\":{\"continuation\":\"next\"}}]}}}").getAsJsonObject();
   check(validate.invoke(null,response).equals("next"),"YouTube continuation was rejected");
  }
  for(String raw:new String[]{"{}","{\"error\":{\"code\":403}}","{\"continuationContents\":{\"liveChatContinuation\":{}}}"}) {
   boolean rejected=false;
   try{validate.invoke(null,com.google.gson.JsonParser.parseString(raw).getAsJsonObject());}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof IOException;}
   check(rejected,"Failed or ended YouTube polling response was accepted");
  }
  System.out.println("YouTube valid polling, errors, and ended-chat responses: PASS");
  com.hristrix.chatrix.integration.tiktok.TikTokStateCheck.run();
  if(args.length==0) return;
  var pipeline=new ChatMessagePipeline();
  var twitch=new TwitchChatClient("https://www.twitch.tv/aethelthryth",pipeline);
  var kick=new KickChatClient("https://kick.com/xqc",pipeline);
  try {
   var parse=TwitchChatClient.class.getDeclaredMethod("handlePrivMsg",String.class);parse.setAccessible(true);
   parse.invoke(twitch,"@display-name=Viewer;color=#123456;badges= :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #aethelthryth :hello world");
   check(pipeline.last.text().equals("hello world")&&pipeline.last.authorLogin().equals("viewer"),"Twitch message parsing");
   var parseKick=KickChatClient.class.getDeclaredMethod("handleMessage",String.class);parseKick.setAccessible(true);
   parseKick.invoke(kick,"{\"event\":\"App\\\\Events\\\\ChatMessageEvent\",\"data\":{\"content\":\"hello kick\",\"sender\":{\"username\":\"viewer\"}}}");
   check(pipeline.last.text().equals("hello kick"),"Kick message parsing");
   System.out.println("Twitch and Kick messages reach the pipeline: PASS");
   waitFor(twitch::isConnected,25000);
   System.out.println("Live Twitch aethelthryth join confirmed: PASS");
   waitFor(kick::isConnected,25000);
   System.out.println("Live Kick xqc subscription confirmed: PASS");
   int before=pipeline.count.get();Thread.sleep(8000);
   System.out.println("Live messages delivered during observation: "+(pipeline.count.get()-before));
  }finally {twitch.disconnect();kick.disconnect();}
 }
}
