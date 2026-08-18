package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.domain.BusinessProjectMilestone;
import com.ruoyi.business.domain.BusinessProjectRisk;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ProjectGovernanceCapabilitiesTest
{
    @Mock private IBusinessProjectService service;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setup()
    {
        invocation = new AiCapabilityInvocation(AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);
    }

    @Test
    void milestoneAndRiskChangesAreConfirmationOnlyAndUseBusinessService()
    {
        SaveProjectMilestoneCapability milestone = new SaveProjectMilestoneCapability(service);
        BusinessProjectMilestone savedMilestone = new BusinessProjectMilestone();
        savedMilestone.setProjectId(17L); savedMilestone.setMilestoneId(8L);
        savedMilestone.setMilestoneName("首批上线"); savedMilestone.setStatus("PENDING");
        when(service.saveMilestone(any(), eq(23L), eq("jianglan"), eq(true))).thenReturn(savedMilestone);
        Map<String, Object> milestoneInput = map("projectId", 17L, "milestoneName", "首批上线",
            "planDate", "2026-09-01", "status", "PENDING", "weight", 30);

        Map<String, Object> milestoneResult = milestone.executeConfirmed(invocation, milestoneInput);

        assertEquals(AiCapabilityRisk.CONFIRM_REQUIRED, milestone.risk());
        assertEquals(8L, milestoneResult.get("milestoneId"));
        ArgumentCaptor<BusinessProjectMilestone> milestoneValue = ArgumentCaptor.forClass(BusinessProjectMilestone.class);
        verify(service).saveMilestone(milestoneValue.capture(), eq(23L), eq("jianglan"), eq(true));
        assertEquals("2026-09-01", new java.text.SimpleDateFormat("yyyy-MM-dd").format(milestoneValue.getValue().getPlanDate()));

        SaveProjectRiskCapability risk = new SaveProjectRiskCapability(service);
        BusinessProjectRisk savedRisk = new BusinessProjectRisk(); savedRisk.setProjectId(17L); savedRisk.setRiskId(9L);
        savedRisk.setRiskTitle("延期"); savedRisk.setStatus("OPEN");
        when(service.saveRisk(any(), eq(23L), eq("jianglan"), eq(true))).thenReturn(savedRisk);
        Map<String, Object> riskResult = risk.executeConfirmed(invocation, map("projectId", 17L,
            "riskTitle", "延期", "severity", "HIGH", "probability", "MEDIUM", "status", "OPEN"));
        assertEquals(9L, riskResult.get("riskId"));
    }

    @Test
    void leaveAndEffortReviewValidateNaturalLanguageArgumentsBeforeExecution()
    {
        SetProjectMemberLeaveCapability leave = new SetProjectMemberLeaveCapability(service);
        Map<String, Object> missingReason = map("operation", "MARK", "projectId", 17L,
            "memberUserId", 66L, "leaveDate", "2026-08-19");
        assertThrows(ServiceException.class, () -> leave.confirmationSummary(invocation, missingReason));

        Map<String, Object> cancel = map("operation", "CANCEL", "projectId", 17L,
            "memberUserId", 66L, "memberName", "施柳浩", "leaveDate", "2026-08-19");
        leave.executeConfirmed(invocation, cancel);
        verify(service).cancelMemberLeave(eq(17L), eq(66L), any(), eq(23L), eq("jianglan"), eq(true));

        ReviewProjectMemberEffortCapability review = new ReviewProjectMemberEffortCapability(service);
        BusinessProjectEffort returned = new BusinessProjectEffort(); returned.setProjectId(17L);
        returned.setUserId(66L); returned.setReportStatus("RETURNED");
        when(service.returnMemberEffort(eq(17L), eq(66L), any(), eq("请补充凭证"), eq(23L), eq("jianglan"), eq(true)))
            .thenReturn(returned);
        Map<String, Object> result = review.executeConfirmed(invocation, map("decision", "RETURN",
            "projectId", 17L, "memberUserId", 66L, "bizDate", "2026-08-18", "reviewComment", "请补充凭证"));
        assertEquals("RETURNED", result.get("status"));
    }

    @Test
    void removalCapabilitiesOnlyUseStableIds()
    {
        new RemoveProjectMilestoneCapability(service).executeConfirmed(invocation,
            map("projectId", 17L, "milestoneId", 8L, "milestoneName", "首批上线"));
        new RemoveProjectRiskCapability(service).executeConfirmed(invocation,
            map("projectId", 17L, "riskId", 9L, "riskTitle", "延期"));

        verify(service).deleteMilestone(17L, 8L, 23L, true);
        verify(service).deleteRisk(17L, 9L, 23L, true);
    }

    private Map<String, Object> map(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }
}
