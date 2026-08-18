package com.ruoyi.business.ai;

import java.util.Locale;

/**
 * Canonical read-only query types understood by the boss AI semantic layer.
 */
public enum BusinessAiQueryType
{
    TODAY_ACCOUNTING(false),
    PROJECT_PORTFOLIO(false),
    PENDING_DECISIONS(false),
    STAFF_OVERVIEW(false),
    PROJECT_DETAIL(true),
    PROJECT_ACCOUNTING(true),
    PROJECT_BUDGET(true),
    MEMBER_PROGRESS(true);

    private final boolean projectReferenceRequired;

    BusinessAiQueryType(boolean projectReferenceRequired)
    {
        this.projectReferenceRequired = projectReferenceRequired;
    }

    /**
     * Whether this query must be resolved to one project before it is executed.
     */
    public boolean requiresProjectReference()
    {
        return projectReferenceRequired;
    }

    /**
     * Parses a model-facing value without accepting arbitrary enum ordinals.
     */
    public static BusinessAiQueryType fromValue(String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("query type is required");
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.length() == 0)
        {
            throw new IllegalArgumentException("query type is required");
        }
        try
        {
            return valueOf(normalized);
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("unsupported query type: " + normalized, ex);
        }
    }
}
