package dev.justaclient.platform.input;
public record CharacterEvent(int codepoint, int modifiers) {
    public String codepointAsString() { return new String(Character.toChars(codepoint)); }
    public boolean isAllowedChatCharacter() { return codepoint >= 32 && codepoint != 127 && codepoint != 167; }
}
