import com.hristrix.chatrix.messages.*;
import java.util.List;

public class EmojiCheck {
    private static String sequence(int... points) {
        return new String(points, 0, points.length);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String germany = sequence(0x1F1E9, 0x1F1EA);
        String france = sequence(0x1F1EB, 0x1F1F7);
        String england = sequence(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F);
        String rainbow = sequence(0x1F3F3, 0xFE0F, 0x200D, 0x1F308);
        String trans = sequence(0x1F3F3, 0xFE0F, 0x200D, 0x26A7, 0xFE0F);
        for (String flag : List.of(germany, france, england, rainbow, trans)) {
            var parsed = UnicodeEmojiParser.parse("Hi " + flag + "!", List.of());
            check(parsed.size() == 1, "One emote per flag");
            var emote = parsed.getFirst();
            check(emote.getStartIndex() == 3 && emote.getEndIndex() == 2 + flag.length(), "UTF-16 flag range");
            check(emote.getEmoteCode().equals(flag), "Complete flag sequence");
            check(EmojiTextureCache.sequenceFromCacheId(emote.getEmoteId()).equals(flag), "Cache round trip");
            check(UnicodeEmojiParser.parse(flag, List.of(new MessageEmote("tiktok", "1", "sticker", 0, flag.length() - 1))).isEmpty(), "Sticker overlap");
        }
        check(UnicodeEmojiParser.parse(germany + france, List.of()).size() == 2, "Adjacent flags");
        check(UnicodeEmojiParser.parse(sequence(0x1F1E9), List.of()).isEmpty(), "Lone regional indicator");
        String unfinished = sequence(0x1F3F4, 0xE0067);
        check(UnicodeEmojiParser.parse(unfinished, List.of()).getFirst().getEndIndex() == 1, "Unterminated tag sequence");
        check(UnicodeEmojiParser.parse(sequence(0x1F3F4, 0x200D) + "hello", List.of()).getFirst().getEndIndex() == 1, "Unjoined ZWJ");
        var resolver = EmojiTextureCache.class.getDeclaredMethod("resolveEmojiGlyph", String.class);
        resolver.setAccessible(true);
        check(resolver.invoke(null, sequence(0x10FFFF)) != null, "Unsupported glyph fallback");
        check(EmojiTextureCache.rasterizeToPng(germany) != null, "Flag PNG rasterization");
        System.out.println("Emoji checks passed");
    }
}
