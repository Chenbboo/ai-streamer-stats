package com.ruoyi.business.ai.capability.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapabilityActionService;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRegistry;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.business.service.IBusinessStaffService;

@ExtendWith(MockitoExtension.class)
class ResetStaffPasswordCapabilityTest
{
    @Mock private IBusinessStaffService staffService;
    @Mock private BusinessAiMapper mapper;

    @Test
    void plaintextPasswordIsRedactedFromAuditAndNeverPersistedInConfirmation()
    {
        Map<String, Object> staff = new LinkedHashMap<String, Object>(); staff.put("userId", 66L);
        staff.put("userName", "shiliuhao"); staff.put("nickName", "施柳浩");
        when(staffService.listOptions()).thenReturn(Collections.singletonList(staff));
        ResetStaffPasswordCapability capability = new ResetStaffPasswordCapability(staffService);
        AiCapabilityRegistry registry = new AiCapabilityRegistry(Collections.singletonList(capability));
        AiCapabilityActionService actions = new AiCapabilityActionService(registry, mapper, new ObjectMapper());
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(
            AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);
        Map<String, Object> input = new LinkedHashMap<String, Object>(); input.put("staffUserId", 66L);
        input.put("newPassword", "safe123456");

        Map<String, Object> prepared = actions.prepare(capability, invocation, input);

        assertEquals("******", ((Map<?, ?>) prepared.get("details")).get("password"));
        assertEquals("******", capability.auditInput(input).get("newPassword"));
        ArgumentCaptor<Map<String, Object>> row = mapCaptor(); verify(mapper).insertActionRequest(row.capture());
        String payload = String.valueOf(row.getValue().get("actionPayloadJson"));
        assertFalse(payload.contains("safe123456")); assertFalse(payload.contains("newPassword"));
        assertTrue(payload.contains("encodedPassword"));
    }

    @Test
    void confirmedResetUsesOnlyTheOneWayEncodedPassword()
    {
        ResetStaffPasswordCapability capability = new ResetStaffPasswordCapability(staffService);
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(
            AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);
        Map<String, Object> input = new LinkedHashMap<String, Object>(); input.put("staffUserId", 66L);
        input.put("encodedPassword", "$2a$10$12345678901234567890123456789012345678901234567890123");

        capability.executeConfirmed(invocation, input);

        verify(staffService).resetEncodedPassword(eq(66L), eq(String.valueOf(input.get("encodedPassword"))), eq("jianglan"));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArgumentCaptor<Map<String, Object>> mapCaptor()
    { return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class); }
}
