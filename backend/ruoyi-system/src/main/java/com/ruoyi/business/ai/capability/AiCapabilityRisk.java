package com.ruoyi.business.ai.capability;

/** AI capability risk determines whether the operation may execute immediately. */
public enum AiCapabilityRisk
{
    READ_ONLY(false),
    DRAFT_WRITE(false),
    CONFIRM_REQUIRED(true);

    private final boolean confirmationRequired;

    AiCapabilityRisk(boolean confirmationRequired)
    {
        this.confirmationRequired = confirmationRequired;
    }

    public boolean isConfirmationRequired()
    {
        return confirmationRequired;
    }
}
