package org.example.kakaoworkbot.models.kakaowork.messages;

import org.example.kakaoworkbot.models.kakaowork.messages.blocks.Block;

import java.util.List;

public class MessagesSendRequest {
    public MessagesSendRequest(String conversationId, String text) {
        conversation_id = conversationId;
        this.text = text;
    }

    public List<Block> blocks;

    private final String conversation_id;
    private final String text;
}
