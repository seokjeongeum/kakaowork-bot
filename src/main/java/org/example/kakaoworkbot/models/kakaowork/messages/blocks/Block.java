package org.example.kakaoworkbot.models.kakaowork.messages.blocks;

public abstract class Block {
    protected Block(String type) {
        this.type = type;
    }

    private final String type;
}
