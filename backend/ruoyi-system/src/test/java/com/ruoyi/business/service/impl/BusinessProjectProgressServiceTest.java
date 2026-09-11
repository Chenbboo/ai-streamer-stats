package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectProgressServiceTest {
    @Mock BusinessProjectMapper mapper;
    @Mock BusinessProjectProgressMapper progressMapper;
    @InjectMocks BusinessProjectServiceImpl service;
    BusinessProject child, parent;
    @BeforeEach void setup() {
        child=new BusinessProject();child.setProjectId(20L);child.setParentId(10L);child.setMainOwnerUserId(9L);
        child.setProjectName("子项目");child.setStatus("ACTIVE");child.setDelFlag("0");
        parent=new BusinessProject();parent.setProjectId(10L);parent.setMainOwnerUserId(8L);parent.setStatus("ACTIVE");
        lenient().when(mapper.selectProjectByIdForUpdate(20L)).thenReturn(child);
    }
    BusinessProjectProgressReport report() {
        BusinessProjectProgressReport report=new BusinessProjectProgressReport();
        report.setProjectId(20L);report.setProgress(40);report.setCompletionSummary("完成第一阶段");
        report.setIssuesRisks("无");report.setNextPlan("完成第二阶段");return report;
    }
    @Test void administratorAndParentOwnerCannotSubmitOnBehalfOfChildOwner() {
        assertThrows(ServiceException.class,()->service.submitProjectProgressReport(report(),1L,"admin",true));
        assertThrows(ServiceException.class,()->service.submitProjectProgressReport(report(),8L,"parent",false));
        verify(mapper,never()).insertProjectProgressReport(any());
        verifyNoInteractions(progressMapper);
    }
    @Test void correctionAppendsVersionAndUsesServerIdentityTimeAndSnapshot() {
        BusinessProjectProgressReport previous=report();previous.setReportId(30L);previous.setVersion(3);previous.setProgress(80);
        when(mapper.selectLatestProjectProgressReport(20L)).thenReturn(previous);
        when(mapper.selectProjectById(10L)).thenReturn(parent);
        Map<String,Object> user=new HashMap<>();user.put("userName","owner");user.put("nickName","真实负责人");
        when(mapper.selectActiveUserById(9L)).thenReturn(user);
        BusinessProjectTask task=new BusinessProjectTask();task.setTaskName("已完成工作");task.setProgress(100);
        when(mapper.selectTasks(20L)).thenReturn(Collections.singletonList(task));
        doAnswer(call->{((BusinessProjectProgressReport)call.getArgument(0)).setReportId(31L);return 1;})
            .when(mapper).insertProjectProgressReport(any());
        BusinessProjectProgressReport input=report();input.setReportId(30L);input.setVersion(999);
        input.setBizDate(new Date(0));input.setCreateTime(new Date(0));input.setSubmittedUserId(1L);
        input.setSubmittedUserName("冒名");input.setSnapshotJson("伪造");input.setSyncTasks(true);
        BusinessProjectProgressReport saved=service.submitProjectProgressReport(input,9L,"owner",false);
        assertEquals(31L,saved.getReportId());assertEquals(4,saved.getVersion());assertEquals(40,saved.getProgress());
        assertEquals(9L,saved.getSubmittedUserId());assertEquals("真实负责人",saved.getSubmittedUserName());
        assertTrue(saved.getCreateTime().getTime()>0);assertEquals(saved.getBizDate(),saved.getCreateTime());
        assertTrue(saved.getSnapshotJson().contains("已完成工作"));assertFalse(saved.getSnapshotJson().contains("伪造"));
        assertEquals(80,previous.getProgress());assertEquals(3,previous.getVersion());
        verify(mapper,times(2)).insertEvent(any());verify(progressMapper).notifyOwner(31L,8L);
    }
    @Test void requiredFieldsAndProgressRangeRejectBeforeWriting() {
        BusinessProjectProgressReport input=report();input.setIssuesRisks(" ");
        assertThrows(ServiceException.class,()->service.submitProjectProgressReport(input,9L,"owner",false));
        input.setIssuesRisks("无");input.setProgress(101);
        assertThrows(ServiceException.class,()->service.submitProjectProgressReport(input,9L,"owner",false));
        verify(mapper,never()).insertProjectProgressReport(any());
    }
    @Test void parentOwnerGetsReadOnlyHistoryWithoutBeingMadeChildManager() {
        when(mapper.selectProjectById(20L)).thenReturn(child);
        when(progressMapper.archiveProject(10L)).thenReturn(parent);
        Map<String,Object> workspace=service.progressWorkspace(20L,8L,false,false);
        assertEquals(false,workspace.get("canSubmit"));
        verify(progressMapper).history(20L);
        verify(mapper,never()).selectMemberRole(anyLong(),anyLong());
    }
    @Test void strangerCannotReadReportHistory() {
        when(mapper.selectProjectById(20L)).thenReturn(child);
        when(progressMapper.archiveProject(10L)).thenReturn(parent);
        assertThrows(ServiceException.class,()->service.progressWorkspace(20L,99L,false,false));
        verify(progressMapper,never()).history(anyLong());
    }
    @Test void archivedChildStillExposesReportsToParentOwnerButCannotSubmit() {
        child.setDelFlag("2");when(progressMapper.archiveProject(20L)).thenReturn(child);
        when(progressMapper.archiveProject(10L)).thenReturn(parent);
        assertEquals(false,service.progressWorkspace(20L,8L,false,false).get("canSubmit"));
        verify(progressMapper).history(20L);
    }
    @Test void childOwnerCannotChangeAggregationWeight() {
        when(mapper.selectProjectByIdForUpdate(10L)).thenReturn(parent);
        assertThrows(ServiceException.class,()->service.setProgressWeight(10L,20L,java.math.BigDecimal.TEN,9L,"child"));
        verify(progressMapper,never()).setWeight(anyLong(),anyLong(),any(),anyString());
    }
    @Test void formerParentOwnerSeesOnlyPreviouslyReceivedVersionsAndNoLiveSnapshot() {
        when(mapper.selectProjectById(20L)).thenReturn(child);
        when(progressMapper.archiveProject(10L)).thenReturn(parent);
        BusinessProjectProgressReport received=report();received.setProjectNameSnapshot("历史项目名");
        when(progressMapper.recipientHistory(20L,7L)).thenReturn(Collections.singletonList(received));
        Map<String,Object> data=service.progressWorkspace(20L,7L,false,false);
        assertEquals(false,data.get("canSubmit"));assertFalse(data.containsKey("snapshot"));
        assertEquals(Collections.singletonList(received),data.get("reports"));
        verify(progressMapper,never()).history(anyLong());verify(mapper,never()).selectTasks(anyLong());
    }
}
