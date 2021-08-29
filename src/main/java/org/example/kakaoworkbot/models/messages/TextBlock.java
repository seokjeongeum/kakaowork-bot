package org.example.kakaoworkbot.models.messages;

public class TextBlock extends Block {
    private final String text;
    private final boolean markdown;

    public TextBlock(String text, boolean markdown) {
        super("text");
        this.text = text;
        this.markdown = markdown;
    }
}
