package org.example.kakaoworkbot.models.messages;

public class HeaderBlock extends Block {
    private final String text;
    private final String style;

    public HeaderBlock(String text, String style) {
        super("header");
        this.text = text;
        this.style = style;
    }
}
