package org.example.kakaoworkbot.models.messages.blocks;

public class ButtonBlock extends Block {
    public ButtonBlock(String text, String style) {
        super("button");
        this.text = text;
        this.style = style;
    }

    public enum ActionType {
        open_inapp_browser, open_system_browser, open_external_app, call_modal, submit_action
    }

    public ActionType action_type;
    public String action_name;
    public String value;

    private final String text;
    private final String style;
}
