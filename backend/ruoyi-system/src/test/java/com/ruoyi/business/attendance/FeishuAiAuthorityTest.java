package com.ruoyi.business.attendance;

import static com.ruoyi.business.attendance.FeishuAttendanceClient.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.project.SetProjectMemberLeaveCapability;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectMember;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

class FeishuAiAuthorityTest
{
    private final IBusinessProjectService projects=mock(IBusinessProjectService.class);
    private final BusinessFeishuService attendance=mock(BusinessFeishuService.class);
    private final SetProjectMemberLeaveCapability capability=new SetProjectMemberLeaveCapability(projects,attendance);
    private final AiCapabilityInvocation invocation=new AiCapabilityInvocation(AiExecutionContext.legacy(9L,"manager",true),1L,2L,3L);
    private final Map<String,Object> input=map("operation","MARK","projectId",1L,"memberUserId",7L,"memberName","untrusted-name","leaveDate","2026-09-07","reason","请假事由");

    @Test void previewChecksProjectScopeBeforeReadingPersonAuthority()
    {
        when(projects.getProject(1L,9L,true,true)).thenThrow(new ServiceException("无权访问项目"));
        assertThrows(ServiceException.class,()->capability.confirmationSummary(invocation,input));verifyNoInteractions(attendance);
    }
    @Test void previewRejectsPersonOutsideProjectAndSwitchedFeishuScope()
    {
        BusinessProject project=project();project.setMembers(Collections.emptyList());when(projects.getProject(1L,9L,true,true)).thenReturn(project);
        assertThrows(ServiceException.class,()->capability.confirmationSummary(invocation,input));verifyNoInteractions(attendance);
        when(projects.getProject(1L,9L,true,true)).thenReturn(project());
        when(attendance.getPersonAuthority(eq(7L),eq(110L),any())).thenReturn(map("localLeaveAllowed",false,"source","FEISHU"));
        assertThrows(ServiceException.class,()->capability.confirmationSummary(invocation,input));
        verify(projects,never()).markMemberLeave(anyLong(),anyLong(),any(),anyString(),anyLong(),anyString(),anyBoolean());
    }
    @Test void staleConfirmationRereadsAuthorityAndCannotBypassTransactionalWriteGuard()
    {
        when(projects.getProject(1L,9L,true,true)).thenReturn(project());
        when(attendance.getPersonAuthority(eq(7L),eq(110L),any())).thenReturn(map("localLeaveAllowed",true));
        String preview=capability.confirmationSummary(invocation,input);assertTrue(preview.contains("真实成员"));assertFalse(preview.contains("untrusted-name"));
        // Cutover commits after confirmation was prepared: the final business transaction refuses it.
        doThrow(new ServiceException("该人员已切换飞书")).when(projects).markMemberLeave(eq(1L),eq(7L),any(),eq("请假事由"),eq(9L),eq("manager"),eq(true));
        assertThrows(ServiceException.class,()->capability.executeConfirmed(invocation,input));
        verify(attendance,times(2)).getPersonAuthority(eq(7L),eq(110L),any());
    }
    private BusinessProject project()
    {BusinessProject p=new BusinessProject();p.setCompanyDeptId(110L);BusinessProjectMember m=new BusinessProjectMember();m.setUserId(7L);m.setUserNameSnapshot("真实成员");p.setMembers(Collections.singletonList(m));return p;}
}
