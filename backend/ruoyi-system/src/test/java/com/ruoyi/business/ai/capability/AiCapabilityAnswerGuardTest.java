package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectMember;

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

    @Test
    void acceptsFactsNestedInProjectDomainObject() throws Exception
    {
        BusinessProject project = new BusinessProject();
        project.setProjectId(1L);
        project.setProjectName("越南直播运营");
        project.setCompanyName("越南meimaru公司");
        project.setMainOwnerName("大D");
        project.setPlanStartDate(new SimpleDateFormat("yyyy-MM-dd").parse("2026-04-04"));
        project.setPlanEndDate(new SimpleDateFormat("yyyy-MM-dd").parse("2026-12-31"));
        project.setActualStartDate(new SimpleDateFormat("yyyy-MM-dd").parse("2026-08-12"));
        project.setMemberCount(2);
        BusinessProjectMember owner = member(127L, "大D", "OWNER");
        BusinessProjectMember initiator = member(126L, "王赋章", "MEMBER");
        project.setMembers(Arrays.asList(owner, initiator));
        Map<String, Object> wrapper = map("toolCode", "project.detail.get", "riskLevel", "READ_ONLY",
            "data", map("project", project));

        String answer = "项目名称：越南直播运营。归属公司：越南meimaru公司。主负责人：大D。"
            + "成员：大D。计划周期2026-04-04至2026-12-31，实际开始于2026-08-12，共2人。";

        AiCapabilityAnswerGuard.Validation validation = guard.validate(answer, Collections.singletonList(wrapper));
        assertTrue(validation.isValid(), validation.getViolations().toString());
    }

    @Test
    void rejectsRoutineAssigneesMisreportedAsProjectMembers() throws Exception
    {
        BusinessProject project = projectWithMembers();
        Map<String, Object> routine = map("assigneeName", "Gold", "cumulativeActual", 4);
        project.setRoutines(Collections.singletonList(new com.ruoyi.business.domain.BusinessProjectRoutine()));
        Map<String, Object> data = map("project", project, "routineEvidence", routine);
        Map<String, Object> wrapper = map("toolCode", "project.detail.get", "riskLevel", "READ_ONLY", "data", data);

        String answer = "成员包括大D、王赋章、Gold，成员数量为4人。";
        AiCapabilityAnswerGuard.Validation validation = guard.validate(answer, Collections.singletonList(wrapper));

        assertFalse(validation.isValid());
        assertTrue(validation.getViolations().toString().contains("Gold"));
        assertTrue(validation.getViolations().toString().contains("项目成员数量"));
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

    private BusinessProjectMember member(Long userId, String name, String role)
    {
        BusinessProjectMember member = new BusinessProjectMember();
        member.setUserId(userId);
        member.setUserNameSnapshot(name);
        member.setMemberRole(role);
        return member;
    }

    private BusinessProject projectWithMembers() throws Exception
    {
        BusinessProject project = new BusinessProject();
        project.setProjectName("越南直播运营");
        project.setPlanStartDate(new SimpleDateFormat("yyyy-MM-dd").parse("2026-04-04"));
        project.setMemberCount(2);
        project.setMembers(Arrays.asList(member(127L, "大D", "OWNER"), member(126L, "王赋章", "MEMBER")));
        return project;
    }
}
