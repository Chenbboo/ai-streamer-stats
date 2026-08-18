package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class AiCapabilityActionServiceTest
{
    @Mock private BusinessAiMapper mapper;
    private AiCapabilityActionService service;
    private ConfirmCapability capability;
    private AiExecutionContext actor;

    @BeforeEach void setup()
    {
        capability = new ConfirmCapability();
        service = new AiCapabilityActionService(new AiCapabilityRegistry(Collections.<AiCapability>singletonList(capability)),
            mapper, new ObjectMapper());
        actor = context("business:test:write");
        lenient().when(mapper.insertActionRequest(any())).thenAnswer(invocation -> {
            ((Map<String,Object>) invocation.getArgument(0)).put("actionRequestId", 88L); return 1; });
    }

    @Test void preparesThenExecutesOnlyThroughConfirmation()
    {
        Map<String,Object> input = Collections.<String,Object>singletonMap("value", "new-value");
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(actor, 7L, 8L, 9L);
        Map<String,Object> prepared = service.prepare(capability, invocation, input);
        assertEquals(88L, prepared.get("actionRequestId"));
        Map<String,Object> action = new LinkedHashMap<String,Object>(); action.put("actionCode", "CAPABILITY:test.confirm");
        action.put("conversationId", 7L); action.put("runId", 8L);
        action.put("actionPayloadJson", "{\"capabilityCode\":\"test.confirm\",\"input\":{\"value\":\"new-value\"}}");
        assertEquals("new-value", service.executeConfirmed(action, actor).get("executed"));
        verify(mapper).insertActionRequest(any());
    }

    @Test void rechecksCurrentPermissionAtConfirmationTime()
    {
        Map<String,Object> action = new LinkedHashMap<String,Object>(); action.put("actionCode", "CAPABILITY:test.confirm");
        action.put("conversationId", 7L); action.put("runId", 8L);
        action.put("actionPayloadJson", "{\"input\":{\"value\":\"new-value\"}}");
        assertThrows(ServiceException.class, () -> service.executeConfirmed(action, context("business:test:read")));
    }

    private AiExecutionContext context(String permission)
    {
        SysUser user = new SysUser(); user.setUserId(23L); user.setUserName("jianglan");
        return AiExecutionContext.from(new LoginUser(23L, 100L, user, Collections.singleton(permission)));
    }

    private static class ConfirmCapability implements AiConfirmableCapability
    {
        public String code(){return "test.confirm";} public String description(){return "test";}
        public String requiredPermission(){return "business:test:write";}
        public Map<String,Object> inputSchema(){return AiSchemas.object();}
        public String confirmationSummary(AiCapabilityInvocation i,Map<String,Object> in){return "确认测试";}
        public Map<String,Object> executeConfirmed(AiCapabilityInvocation i,Map<String,Object> in)
        {return Collections.<String,Object>singletonMap("executed",in.get("value"));}
    }
}
