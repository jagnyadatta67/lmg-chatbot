package com.lmg.online.chatbot.ai.deprecated.controller.intent;

public enum ChatIntent {
    ORDER_TRACKING("track", "where", "order status", "my order", "delivery"),
    ORDER_CANCELLATION("cancel", "cancel order", "stop order"),
    POLICY_QUESTION("return", "exchange", "refund", "policy", "shipping", "delivery time"),
    STORE_LOCATOR("store", "near me", "near by", "offstore", "mall"),
    GENERAL_QUERY("help", "support", "contact"),
    CUSTOMER_PROFILE("my profile", "myprofile", "profile","personal details","my details","account","my account"),
    GIFT_CARD_BALANCE("gift card", "giftcard", "gc balance", "gift card balance", "check balance", "card balance", "voucher balance");


    private final String[] keywords;

    ChatIntent(String... keywords) {
        this.keywords = keywords;
    }

    public String[] getKeywords() {
        return keywords;
    }
}
