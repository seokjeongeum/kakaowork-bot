package org.example.kakaoworkbot.models.messages.blocks;

public class HeaderBlock extends Block {
    public HeaderBlock(String text, Style style) {
        super("header");
        this.text = text;
        this.style = style;
    }

    public enum Style {
        blue, red, yellow
    }

    private final String text;
    private final Style style;
}
