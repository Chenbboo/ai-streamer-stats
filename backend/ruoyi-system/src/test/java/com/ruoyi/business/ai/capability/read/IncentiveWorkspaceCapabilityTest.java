package com.ruoyi.business.ai.capability.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.service.IBusinessIncentiveService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class IncentiveWorkspaceCapabilityTest
{
    @Mock IBusinessIncentiveService service;

    @Test void authorizedReadReusesServiceScopeAndCannotMutate()
    {
        Map<String,Object> workspace=Collections.<String,Object>singletonMap("paymentStatus","NOT_RECORDED");
        when(service.workspace(17L,9L,false)).thenReturn(workspace);
        IncentiveWorkspaceCapability capability=new IncentiveWorkspaceCapability(service);
        Map<String,Object> result=capability.execute(invocation(9L,true),Collections.<String,Object>singletonMap("projectId",17L));
        assertEquals(workspace,result.get("workspace"));assertEquals(AiCapabilityRisk.READ_ONLY,capability.risk());
        assertEquals("business:incentive:list",capability.requiredPermission());
        verify(service).workspace(17L,9L,false);
    }

    @Test void missingPermissionCannotEvenCallRewardWorkspace()
    {
        IncentiveWorkspaceCapability capability=new IncentiveWorkspaceCapability(service);
        assertThrows(ServiceException.class,()->capability.execute(invocation(9L,false),Collections.emptyMap()));
        verify(service,never()).workspace(any(),any(),anyBoolean());
    }

    @Test void businessScopeFailurePropagatesAndAdminFlagIsExplicit()
    {
        when(service.workspace(17L,19L,false)).thenThrow(new ServiceException("无权查看"));
        assertThrows(ServiceException.class,()->new IncentiveWorkspaceCapability(service).execute(invocation(19L,true),
            Collections.<String,Object>singletonMap("projectId",17L)));
        new IncentiveWorkspaceCapability(service).execute(invocation(1L,true),Collections.emptyMap());
        verify(service).workspace(null,1L,true);
    }

    private AiCapabilityInvocation invocation(Long userId,boolean permission)
    {
        SysUser user=new SysUser();user.setUserId(userId);user.setUserName("reader");
        LoginUser login=new LoginUser();login.setUserId(userId);login.setUser(user);
        login.setPermissions(permission?Collections.singleton("business:incentive:list"):Collections.emptySet());
        return new AiCapabilityInvocation(AiExecutionContext.from(login),1L,2L,3L);
    }
}
