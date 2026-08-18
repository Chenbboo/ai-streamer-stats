package com.ruoyi.business.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, validated query contract between natural-language understanding and
 * the boss AI data tools.
 *
 * <p>The class deliberately accepts only scalar values for identifiers and names.
 * It does not turn arbitrary model output into strings.</p>
 */
public final class BusinessAiSemanticQuery
{
    private static final DateTimeFormatter BUSINESS_DATE_FORMAT =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final BusinessAiQueryType queryType;
    private final Long projectId;
    private final String projectName;
    private final String personName;
    private final LocalDate bizDate;

    private BusinessAiSemanticQuery(BusinessAiQueryType queryType, Long projectId, String projectName,
        String personName, LocalDate bizDate)
    {
        this.queryType = Objects.requireNonNull(queryType, "queryType");
        this.projectId = projectId;
        this.projectName = projectName;
        this.personName = personName;
        this.bizDate = bizDate;
    }

    /**
     * Safely parses a map produced by an AI model or JSON decoder.
     *
     * <p>The canonical type key is {@code queryType}. {@code type} is accepted as
     * a compatibility alias, but contradictory values are rejected.</p>
     *
     * @param source untrusted semantic-query values
     * @return normalized immutable query
     * @throws BusinessAiSemanticQueryParseException when a known field is invalid
     */
    public static BusinessAiSemanticQuery fromMap(Map<String, ?> source)
    {
        if (source == null)
        {
            throw invalid("query", "semantic query must not be null");
        }

        BusinessAiQueryType queryType = parseQueryType(source);
        Long projectId = parseProjectId(source.get("projectId"));
        String projectName = parseOptionalText(source.get("projectName"), "projectName");
        String personName = parseOptionalText(source.get("personName"), "personName");
        LocalDate bizDate = parseBusinessDate(source.get("bizDate"));
        return new BusinessAiSemanticQuery(queryType, projectId, projectName, personName, bizDate);
    }

    private static BusinessAiQueryType parseQueryType(Map<String, ?> source)
    {
        Object canonical = source.get("queryType");
        Object alias = source.get("type");
        BusinessAiQueryType canonicalType = parseQueryTypeValue(canonical, canonical != null);
        BusinessAiQueryType aliasType = parseQueryTypeValue(alias, alias != null);
        if (canonicalType != null && aliasType != null && canonicalType != aliasType)
        {
            throw invalid("queryType", "queryType and type must describe the same query");
        }
        BusinessAiQueryType result = canonicalType == null ? aliasType : canonicalType;
        if (result == null)
        {
            throw invalid("queryType", "queryType is required");
        }
        return result;
    }

    private static BusinessAiQueryType parseQueryTypeValue(Object raw, boolean supplied)
    {
        if (!supplied)
        {
            return null;
        }
        if (raw instanceof BusinessAiQueryType)
        {
            return (BusinessAiQueryType) raw;
        }
        if (!(raw instanceof CharSequence))
        {
            throw invalid("queryType", "queryType must be a string");
        }
        try
        {
            return BusinessAiQueryType.fromValue(raw.toString());
        }
        catch (IllegalArgumentException ex)
        {
            throw invalid("queryType", ex.getMessage(), ex);
        }
    }

    private static Long parseProjectId(Object raw)
    {
        if (raw == null)
        {
            return null;
        }
        String value;
        if (raw instanceof CharSequence)
        {
            value = raw.toString().trim();
            if (value.length() == 0)
            {
                return null;
            }
            if (!value.matches("[0-9]+"))
            {
                throw invalid("projectId", "projectId must be a positive whole number");
            }
        }
        else if (raw instanceof Number)
        {
            value = raw.toString();
        }
        else
        {
            throw invalid("projectId", "projectId must be a positive whole number");
        }

        try
        {
            long parsed = new BigDecimal(value).longValueExact();
            if (parsed <= 0L)
            {
                throw invalid("projectId", "projectId must be greater than zero");
            }
            return parsed;
        }
        catch (ArithmeticException | NumberFormatException ex)
        {
            throw invalid("projectId", "projectId must be a positive whole number within the Long range", ex);
        }
    }

    private static String parseOptionalText(Object raw, String field)
    {
        if (raw == null)
        {
            return null;
        }
        if (!(raw instanceof CharSequence))
        {
            throw invalid(field, field + " must be a string");
        }
        String value = raw.toString().trim();
        return value.length() == 0 ? null : value;
    }

    private static LocalDate parseBusinessDate(Object raw)
    {
        if (raw == null)
        {
            return null;
        }
        if (raw instanceof LocalDate)
        {
            return (LocalDate) raw;
        }
        if (!(raw instanceof CharSequence))
        {
            throw invalid("bizDate", "bizDate must use the yyyy-MM-dd format");
        }
        String value = raw.toString().trim();
        if (value.length() == 0)
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value, BUSINESS_DATE_FORMAT);
        }
        catch (DateTimeParseException ex)
        {
            throw invalid("bizDate", "bizDate must be a valid date in yyyy-MM-dd format", ex);
        }
    }

    private static BusinessAiSemanticQueryParseException invalid(String field, String message)
    {
        return new BusinessAiSemanticQueryParseException(field, message);
    }

    private static BusinessAiSemanticQueryParseException invalid(String field, String message, Throwable cause)
    {
        return new BusinessAiSemanticQueryParseException(field, message, cause);
    }

    public BusinessAiQueryType getQueryType()
    {
        return queryType;
    }

    public Long getProjectId()
    {
        return projectId;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public String getPersonName()
    {
        return personName;
    }

    public LocalDate getBizDate()
    {
        return bizDate;
    }

    /**
     * Returns whether the model supplied an ID or a name that can be resolved to
     * a canonical project ID.
     */
    public boolean hasProjectReference()
    {
        return projectId != null || projectName != null;
    }

    /**
     * Returns whether this query still needs a project reference before execution.
     */
    public boolean needsProjectResolution()
    {
        return queryType.requiresProjectReference() && !hasProjectReference();
    }

    /**
     * Exposes normalized scalar values for tool arguments or audit logging.
     */
    public Map<String, Object> toMap()
    {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("queryType", queryType.name());
        if (projectId != null) values.put("projectId", projectId);
        if (projectName != null) values.put("projectName", projectName);
        if (personName != null) values.put("personName", personName);
        if (bizDate != null) values.put("bizDate", bizDate.toString());
        return Collections.unmodifiableMap(values);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof BusinessAiSemanticQuery)) return false;
        BusinessAiSemanticQuery that = (BusinessAiSemanticQuery) other;
        return queryType == that.queryType && Objects.equals(projectId, that.projectId)
            && Objects.equals(projectName, that.projectName) && Objects.equals(personName, that.personName)
            && Objects.equals(bizDate, that.bizDate);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(queryType, projectId, projectName, personName, bizDate);
    }

    @Override
    public String toString()
    {
        return "BusinessAiSemanticQuery" + toMap();
    }
}
