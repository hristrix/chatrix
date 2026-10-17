param([switch]$Live)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $cacheRoot = $env:GRADLE_USER_HOME
    if (-not $cacheRoot) { $cacheRoot = Join-Path $env:USERPROFILE '.gradle' }
    $gson = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1/com.google.code.gson/gson') -Filter '*.jar' -Recurse |
        Where-Object Name -NotLike '*-sources.jar' | Select-Object -First 1
    if (-not $gson) { throw 'Build Chatrix first to download its Gson dependency.' }
    New-Item -ItemType Directory -Path build/chat-check/classes -Force | Out-Null
    Copy-Item -LiteralPath $gson.FullName -Destination build/chat-check/gson.jar
    $sourceRoot = 'src/client/java/com/hristrix/chatrix/'
    $sources = @(
        'integration/chat/ChatChannelName.java', 'integration/chat/SimpleWebSocket.java',
        'integration/chat/ChatConnection.java', 'integration/chat/ChatPlatform.java',
        'integration/chat/ChatAuthorRoles.java', 'integration/chat/ChatEventType.java',
        'integration/chat/IncomingChatMessage.java', 'integration/chat/ChatHttp.java',
        'integration/chat/ChatConnectException.java', 'integration/twitch/TwitchChatClient.java',
        'integration/kick/KickChatClient.java', 'integration/kick/KickEmoteParser.java',
        'messages/MessageEmote.java', 'config/ChatRoleFilter.java',
        'integration/youtube/YouTubeChatClient.java', 'integration/youtube/YouTubeEmoteParser.java',
        'integration/tiktok/TikTokChatClient.java', 'integration/tiktok/TikTokWebSocket.java',
        'integration/tiktok/TikTokWssUrl.java', 'integration/tiktok/TikTokFrames.java',
        'integration/tiktok/TikTokEmoteParser.java', 'integration/tiktok/proto/TikTokProto.java'
    ) | ForEach-Object { $sourceRoot + $_ }
    $stubs = @(Get-ChildItem tests/chat/stubs -Filter '*.java' -Recurse | ForEach-Object FullName)
    & javac -encoding UTF-8 -cp build/chat-check/gson.jar -d build/chat-check/classes @sources @stubs tests/chat/ChatCheck.java tests/chat/TikTokStateCheck.java
    if ($LASTEXITCODE -ne 0) { throw 'Chat check compilation failed.' }
    $classPath = 'build/chat-check/classes' + [IO.Path]::PathSeparator + 'build/chat-check/gson.jar'
    $liveArgs = @()
    if ($Live) { $liveArgs = @('live') }
    & java -cp $classPath ChatCheck @liveArgs
    if ($LASTEXITCODE -ne 0) { throw 'Chat checks failed.' }
} finally {
    Pop-Location
}
