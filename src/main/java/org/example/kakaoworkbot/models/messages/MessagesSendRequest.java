package org.example.kakaoworkbot.models.messages;

import java.util.List;

public class MessagesSendRequest {
    private final String conversation_id;
    private final String text;
    public List<Block> blocks;

    public MessagesSendRequest(String conversationId, String text) {
        conversation_id = conversationId;
        this.text = text;
    }
}
