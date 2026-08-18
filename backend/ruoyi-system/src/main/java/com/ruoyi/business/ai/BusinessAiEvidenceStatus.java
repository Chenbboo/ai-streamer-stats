package com.ruoyi.business.ai;

/**
 * Reliability state of an evidence item, not the business entity status.
 */
public enum BusinessAiEvidenceStatus
{
    CONFIRMED,
    PROVISIONAL,
    MISSING,
    STALE,
    INVALID
}
