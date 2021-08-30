package org.example.kakaoworkbot.models.messages.blocks;

public class TextBlock extends Block {
    public TextBlock(String text, boolean markdown) {
        super("text");
        this.text = text;
        this.markdown = markdown;
    }

    private final String text;
    private final boolean markdown;
}
