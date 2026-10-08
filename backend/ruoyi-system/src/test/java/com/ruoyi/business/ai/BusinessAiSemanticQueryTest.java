package com.ruoyi.business.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusinessAiSemanticQueryTest
{
    @Test
    void parsesAndNormalizesEverySupportedQueryType()
    {
        List<String> values = Arrays.asList("today-accounting", "project portfolio", "PENDING_DECISIONS",
            "staff_overview", "project_detail", "PROJECT_ACCOUNTING", "project-budget", "member progress");

        for (int index = 0; index < values.size(); index++)
        {
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("queryType", values.get(index));
            BusinessAiSemanticQuery query = BusinessAiSemanticQuery.fromMap(source);
            assertEquals(BusinessAiQueryType.values()[index], query.getQueryType());
        }
    }

    @Test
    void parsesCanonicalFieldsAndReturnsImmutableNormalizedMap()
    {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("queryType", "member_progress");
        source.put("projectId", " 42 ");
        source.put("projectName", " 新谷酵素视频剪辑 ");
        source.put("personName", " 石头 ");
        source.put("bizDate", "2026-08-13");

        BusinessAiSemanticQuery query = BusinessAiSemanticQuery.fromMap(source);

        assertEquals(Long.valueOf(42L), query.getProjectId());
        assertEquals("新谷酵素视频剪辑", query.getProjectName());
        assertEquals("石头", query.getPersonName());
        assertEquals(LocalDate.of(2026, 8, 13), query.getBizDate());
        assertTrue(query.hasProjectReference());
        assertFalse(query.needsProjectResolution());
        assertEquals("2026-08-13", query.toMap().get("bizDate"));
        assertThrows(UnsupportedOperationException.class, () -> query.toMap().put("projectId", 9L));
    }

    @Test
    void acceptsTypeAliasAndRejectsContradictoryTypeKeys()
    {
        BusinessAiSemanticQuery query = BusinessAiSemanticQuery.fromMap(
            Collections.<String, Object>singletonMap("type", "staff_overview"));
        assertEquals(BusinessAiQueryType.STAFF_OVERVIEW, query.getQueryType());

        Map<String, Object> contradictory = new LinkedHashMap<String, Object>();
        contradictory.put("queryType", "PROJECT_DETAIL");
        contradictory.put("type", "PROJECT_BUDGET");
        BusinessAiSemanticQueryParseException error = assertThrows(BusinessAiSemanticQueryParseException.class,
            () -> BusinessAiSemanticQuery.fromMap(contradictory));
        assertEquals("queryType", error.getField());
    }

    @Test
    void rejectsMissingAndUnsupportedQueryTypes()
    {
        BusinessAiSemanticQueryParseException missing = assertThrows(BusinessAiSemanticQueryParseException.class,
            () -> BusinessAiSemanticQuery.fromMap(Collections.<String, Object>emptyMap()));
        assertEquals("queryType", missing.getField());

        Map<String, Object> unsupported = new LinkedHashMap<String, Object>();
        unsupported.put("queryType", "DELETE_PROJECT");
        BusinessAiSemanticQueryParseException invalid = assertThrows(BusinessAiSemanticQueryParseException.class,
            () -> BusinessAiSemanticQuery.fromMap(unsupported));
        assertEquals("queryType", invalid.getField());
    }

    @Test
    void validatesBusinessDateStrictly()
    {
        for (Object value : Arrays.<Object>asList("2026-02-30", "2026-8-13", "13/08/2026", Integer.valueOf(20260813)))
        {
            Map<String, Object> source = baseQuery();
            source.put("bizDate", value);
            BusinessAiSemanticQueryParseException error = assertThrows(BusinessAiSemanticQueryParseException.class,
                () -> BusinessAiSemanticQuery.fromMap(source));
            assertEquals("bizDate", error.getField());
        }

        Map<String, Object> valid = baseQuery();
        valid.put("bizDate", LocalDate.of(2024, 2, 29));
        assertEquals(LocalDate.of(2024, 2, 29), BusinessAiSemanticQuery.fromMap(valid).getBizDate());
    }

    @Test
    void rejectsUnsafeOrInvalidProjectIdentifiers()
    {
        for (Object value : Arrays.<Object>asList(Long.valueOf(0L), Long.valueOf(-1L), new BigDecimal("1.5"),
            "12.5", "9223372036854775808", Boolean.TRUE))
        {
            Map<String, Object> source = baseQuery();
            source.put("projectId", value);
            BusinessAiSemanticQueryParseException error = assertThrows(BusinessAiSemanticQueryParseException.class,
                () -> BusinessAiSemanticQuery.fromMap(source));
            assertEquals("projectId", error.getField());
        }
    }

    @Test
    void doesNotStringifyStructuredValuesIntoNames()
    {
        Map<String, Object> source = baseQuery();
        source.put("personName", Arrays.asList("石头", "蒋豪"));

        BusinessAiSemanticQueryParseException error = assertThrows(BusinessAiSemanticQueryParseException.class,
            () -> BusinessAiSemanticQuery.fromMap(source));

        assertEquals("personName", error.getField());
    }

    @Test
    void normalizesBlankOptionalValuesAndReportsMissingProjectResolution()
    {
        Map<String, Object> source = baseQuery();
        source.put("projectId", " ");
        source.put("projectName", " ");
        source.put("personName", "");
        source.put("bizDate", " ");

        BusinessAiSemanticQuery query = BusinessAiSemanticQuery.fromMap(source);

        assertNull(query.getProjectId());
        assertNull(query.getProjectName());
        assertNull(query.getPersonName());
        assertNull(query.getBizDate());
        assertTrue(query.needsProjectResolution());
    }

    @Test
    void portfolioQueryDoesNotRequireProjectResolution()
    {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("queryType", BusinessAiQueryType.PROJECT_PORTFOLIO);

        BusinessAiSemanticQuery query = BusinessAiSemanticQuery.fromMap(source);

        assertFalse(query.hasProjectReference());
        assertFalse(query.needsProjectResolution());
    }

    private Map<String, Object> baseQuery()
    {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("queryType", "PROJECT_DETAIL");
        return source;
    }
}
