package org.example.kakaoworkbot.models.messages;

public abstract class Block {
    private final String type;

    protected Block(String type) {
        this.type = type;
    }
}
