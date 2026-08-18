package com.ruoyi.business.ai.capability.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ProjectDraftCapabilitiesTest
{
    @Mock private BusinessAiMapper mapper;
    private GetActiveProjectDraftCapability getCapability;
    private UpdateActiveProjectDraftCapability updateCapability;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setup()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        getCapability = new GetActiveProjectDraftCapability(mapper, objectMapper);
        updateCapability = new UpdateActiveProjectDraftCapability(mapper, objectMapper);
        SysUser user = new SysUser(); user.setUserId(23L); user.setUserName("jianglan");
        LoginUser login = new LoginUser(23L, 100L, user, Collections.singleton("business:project:add"));
        invocation = new AiCapabilityInvocation(AiExecutionContext.from(login), 67L, 90L, 375L);
    }

    @Test
    void updateUsesStructuredChangesAndRecordsBeforeAndAfter()
    {
        when(mapper.selectActiveWorkflow(67L, 23L)).thenReturn(workflow());
        when(mapper.updateWorkflow(any())).thenReturn(1);
        Map<String, Object> changes = new LinkedHashMap<String, Object>();
        changes.put("objective", "完成600条视频");

        Map<String, Object> result = updateCapability.execute(invocation,
            Collections.<String, Object>singletonMap("changes", changes));

        assertEquals("完成600条视频", ((Map<?, ?>) result.get("draft")).get("objective"));
        ArgumentCaptor<Map<String, Object>> event = mapCaptor();
        verify(mapper).insertWorkflowEvent(event.capture());
        assertEquals("FIELDS_UPDATED", event.getValue().get("eventType"));
        assertEquals(true, String.valueOf(event.getValue().get("beforeJson")).contains("完成1000条视频"));
        assertEquals(true, String.valueOf(event.getValue().get("afterJson")).contains("完成600条视频"));
    }

    @Test
    void changingAReadyDraftInvalidatesTheOldConfirmation()
    {
        Map<String, Object> workflow = workflow();
        workflow.put("workflowStatus", "WAITING_CONFIRMATION");
        workflow.put("actionRequestId", 88L);
        when(mapper.selectActiveWorkflow(67L, 23L)).thenReturn(workflow);
        when(mapper.updateWorkflow(any())).thenReturn(1);
        Map<String, Object> changes = Collections.<String, Object>singletonMap("objective", "完成600条视频");

        Map<String, Object> result = updateCapability.execute(invocation,
            Collections.<String, Object>singletonMap("changes", changes));

        assertEquals("COLLECTING", result.get("status"));
        assertEquals(null, result.get("actionRequestId"));
        verify(mapper).supersedeActionRequest(88L, 23L);
    }

    @Test
    void rejectsFieldsOutsideTheDraftWhitelist()
    {
        Map<String, Object> changes = Collections.<String, Object>singletonMap("userId", 1L);
        assertThrows(ServiceException.class, () -> updateCapability.execute(invocation,
            Collections.<String, Object>singletonMap("changes", changes)));
    }

    @Test
    void getAlwaysUsesConversationAndLoggedInUserToLocateDraft()
    {
        when(mapper.selectActiveWorkflow(67L, 23L)).thenReturn(workflow());
        Map<String, Object> result = getCapability.execute(invocation, Collections.<String, Object>emptyMap());
        assertEquals(3L, result.get("workflowId"));
        assertEquals("完成1000条视频", ((Map<?, ?>) result.get("draft")).get("objective"));
    }

    private Map<String, Object> workflow()
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("workflowId", 3L); row.put("workflowCode", "CREATE_PROJECT");
        row.put("workflowStatus", "COLLECTING"); row.put("currentStep", "ACCOUNTING_AND_BUDGET");
        row.put("versionNo", 7L); row.put("userId", 23L); row.put("conversationId", 67L);
        row.put("draftJson", "{\"projectName\":\"王老吉视频制作\",\"objective\":\"完成1000条视频\"}");
        row.put("missingFieldsJson", "[]"); row.put("boundEntitiesJson", "{}");
        return row;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<Map<String, Object>> mapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
