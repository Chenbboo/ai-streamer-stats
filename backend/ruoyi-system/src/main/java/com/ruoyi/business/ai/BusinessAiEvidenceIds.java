package com.ruoyi.business.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates stable evidence identifiers from the source identity of a fact.
 */
public final class BusinessAiEvidenceIds
{
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private BusinessAiEvidenceIds()
    {
    }

    public static String deterministic(BusinessAiEvidenceEntityType entityType, String entityId,
            String metricCode, String period, String sourcePath)
    {
        if (entityType == null)
        {
            throw new IllegalArgumentException("entityType must not be null");
        }
        String canonical = entityType.name() + '\u001f'
                + requireText(entityId, "entityId") + '\u001f'
                + requireText(metricCode, "metricCode") + '\u001f'
                + requireText(period, "period") + '\u001f'
                + requireText(sourcePath, "sourcePath");
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            char[] encoded = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++)
            {
                int current = digest[index] & 0xff;
                encoded[index * 2] = HEX[current >>> 4];
                encoded[index * 2 + 1] = HEX[current & 0x0f];
            }
            return "evi_" + new String(encoded);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
