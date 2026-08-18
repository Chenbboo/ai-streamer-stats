package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiCapabilityReferenceGuardTest
{
    private final AiCapabilityReferenceGuard guard = new AiCapabilityReferenceGuard();

    @Test
    void rejectsAnInventedProjectIdWhenDirectoryCandidatesExist()
    {
        Map<String, Object> error = guard.validate(row("projectId", 2L),
            Collections.singletonList(toolResult("project.directory.get", "projects", Arrays.asList(
                row("projectId", 16L), row("projectId", 17L)))));

        assertEquals("INVALID_REFERENCE", error.get("errorCode"));
        assertEquals("projectId", error.get("field"));
        assertEquals(Arrays.asList("16", "17"), error.get("allowedValues"));
    }

    @Test
    void acceptsTheStableIdReturnedByThePreviousTool()
    {
        assertNull(guard.validate(row("projectId", 17L),
            Collections.singletonList(toolResult("project.directory.get", "projects",
                Collections.singletonList(row("projectId", 17L))))));
    }

    @Test
    void understandsStaffUserIdAsAUserIdReferenceAndIgnoresNonOpenDirectoryRows()
    {
        List<Map<String, Object>> people = Arrays.asList(row("userId", 147L), row("userId", 143L, "canOpen", false));
        Map<String, Object> error = guard.validate(row("staffUserId", 143L),
            Collections.singletonList(toolResult("staff.directory.get", "staff", people)));

        assertEquals(Collections.singletonList("147"), error.get("allowedValues"));
    }

    @Test
    void directStableIdIsAllowedWhenNoDirectoryWasReadInThisRun()
    {
        assertNull(guard.validate(row("projectId", 17L), Collections.<Map<String, Object>>emptyList()));
    }

    private Map<String, Object> toolResult(String code, String key, Object value)
    { return row("toolCode", code, "data", row(key, value)); }

    private Map<String, Object> row(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
}
