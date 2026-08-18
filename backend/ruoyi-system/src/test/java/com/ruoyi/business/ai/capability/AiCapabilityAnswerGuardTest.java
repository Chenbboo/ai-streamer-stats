package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiCapabilityAnswerGuardTest
{
    private final AiCapabilityAnswerGuard guard = new AiCapabilityAnswerGuard();

    @Test
    void acceptsAmountsDatesCountsAndNamesReturnedByCapabilities()
    {
        String answer = "项目名称：王老吉视频宣传，负责人：石头。计划周期2026-08-14至2026-09-30，预算1,000元，共1个项目。";
        assertTrue(guard.validate(answer, results()).isValid());
    }

    @Test
    void rejectsInventedAmountDateAndPerson()
    {
        String answer = "项目名称：王老吉视频宣传，负责人：李四。结束日期2026-10-31，预算2,000元。";
        AiCapabilityAnswerGuard.Validation validation = guard.validate(answer, results());
        assertFalse(validation.isValid());
        assertTrue(validation.getViolations().toString().contains("李四"));
        assertTrue(validation.getViolations().toString().contains("2026-10-31"));
        assertTrue(validation.getViolations().toString().contains("2,000元"));
    }

    @Test
    void allowsSafeConversationWithoutBusinessEvidence()
    {
        assertTrue(guard.validate("你好，我可以帮你处理公司经营事务。", Collections.emptyList()).isValid());
    }

    private List<Map<String, Object>> results()
    {
        Map<String, Object> project = map("projectId", 17L, "projectName", "王老吉视频宣传",
            "mainOwnerName", "石头", "planStartDate", "2026-08-14", "planEndDate", "2026-09-30",
            "budgetLimit", "1000.00");
        Map<String, Object> wrapper = map("toolCode", "project.detail.get", "riskLevel", "READ_ONLY",
            "data", map("project", project));
        return Collections.singletonList(wrapper);
    }

    private Map<String, Object> map(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
}
