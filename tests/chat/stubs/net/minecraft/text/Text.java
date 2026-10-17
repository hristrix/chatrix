package net.minecraft.text;
public record Text(String value) {
 public static Text translatable(String key,Object...args) { return new Text(key); }
 public String getString() { return value; }
}
