package com.ruoyi.business.domain;

/** 老板 AI 对话请求。 */
public class BusinessAiChatRequest
{
    private Long conversationId;
    private String message;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
