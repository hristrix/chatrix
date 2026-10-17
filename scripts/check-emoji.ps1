$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Path build/emoji-check/classes -Force | Out-Null
    $sources = @('MessageEmote.java', 'UnicodeEmojiParser.java', 'EmojiTextureCache.java') |
        ForEach-Object { 'src/client/java/com/hristrix/chatrix/messages/' + $_ }
    & javac -encoding UTF-8 -d build/emoji-check/classes @sources tests/chat/stubs/com/hristrix/chatrix/ChatrixClient.java tests/emoji/stubs/com/hristrix/chatrix/messages/TwitchEmoteTextureCache.java tests/emoji/EmojiCheck.java
    if ($LASTEXITCODE -ne 0) { throw 'Emoji check compilation failed.' }
    & java '-Djava.awt.headless=true' -cp build/emoji-check/classes EmojiCheck
    if ($LASTEXITCODE -ne 0) { throw 'Emoji checks failed.' }
} finally {
    Pop-Location
}
