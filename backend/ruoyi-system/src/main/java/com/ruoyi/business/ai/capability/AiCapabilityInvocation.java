package com.ruoyi.business.ai.capability;

/** Request-scoped identifiers passed to every capability executor. */
public final class AiCapabilityInvocation
{
    private final AiExecutionContext actor;
    private final Long conversationId;
    private final Long runId;
    private final Long requestMessageId;

    public AiCapabilityInvocation(AiExecutionContext actor, Long conversationId, Long runId,
        Long requestMessageId)
    {
        if (actor == null) throw new IllegalArgumentException("actor must not be null");
        this.actor = actor;
        this.conversationId = conversationId;
        this.runId = runId;
        this.requestMessageId = requestMessageId;
    }

    public AiExecutionContext getActor() { return actor; }
    public Long getConversationId() { return conversationId; }
    public Long getRunId() { return runId; }
    public Long getRequestMessageId() { return requestMessageId; }
}
