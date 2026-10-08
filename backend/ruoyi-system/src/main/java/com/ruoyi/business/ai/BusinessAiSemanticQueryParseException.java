package com.ruoyi.business.ai;

/**
 * Indicates that an untrusted semantic-query map does not satisfy the contract.
 */
public class BusinessAiSemanticQueryParseException extends IllegalArgumentException
{
    private static final long serialVersionUID = 1L;

    private final String field;

    public BusinessAiSemanticQueryParseException(String field, String message)
    {
        super(message);
        this.field = field;
    }

    public BusinessAiSemanticQueryParseException(String field, String message, Throwable cause)
    {
        super(message, cause);
        this.field = field;
    }

    /**
     * Returns the canonical field that failed validation.
     */
    public String getField()
    {
        return field;
    }
}
