package com.ruoyi.business.attendance;

import static com.ruoyi.business.attendance.FeishuAttendanceClient.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.ruoyi.business.mapper.BusinessFeishuMapper;
import com.ruoyi.common.exception.ServiceException;

class BusinessFeishuServiceTest
{
    private BusinessFeishuMapper mapper;
    private AttendanceProvider provider;
    private BusinessFeishuService service;
    @BeforeEach void setup()
    {
        mapper=mock(BusinessFeishuMapper.class);provider=mock(AttendanceProvider.class);
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service=new BusinessFeishuService(mapper,provider,tx);
    }
    @Test void bulkMappingRejectsUnconfirmedAndRevokedScopeBeforeWriting()
    {
        assertThrows(ServiceException.class,()->service.addMappings(1L,map("confirmed",false),9L));
        verifyNoInteractions(provider);
        when(mapper.connection(1L)).thenReturn(connection());when(mapper.lockConnection(1L)).thenReturn(connection());
        when(provider.directory("tenant")).thenReturn(Collections.emptyList());
        Map<String,Object> input=map("confirmed",true,"connectionVersion",2,"effectiveFrom","2026-09-01","items",Arrays.asList(map("userId",7L,"externalUserId","u1")));
        assertThrows(ServiceException.class,()->service.addMappings(1L,input,9L));verify(mapper,never()).insertMapping(anyMap());
    }
    @Test void bulkMappingValidatesAllRowsBeforeAnyWriteAndBumpsVersionOnce()
    {
        when(mapper.connection(1L)).thenReturn(connection());when(mapper.lockConnection(1L)).thenReturn(connection());
        when(provider.directory(anyString())).thenReturn(Arrays.asList(map("externalUserId","u1"),map("externalUserId","u2")));
        when(mapper.staff(7L)).thenReturn(map("delFlag","0","companyDeptId",110L));
        when(mapper.staff(8L)).thenReturn(map("delFlag","0","companyDeptId",111L));
        Map<String,Object> input=map("confirmed",true,"connectionVersion",2,"effectiveFrom","2026-09-01","items",Arrays.asList(map("userId",7L,"externalUserId","u1"),map("userId",8L,"externalUserId","u2")));
        assertThrows(ServiceException.class,()->service.addMappings(1L,input,9L));verify(mapper,never()).insertMapping(anyMap());
        when(mapper.staff(8L)).thenReturn(map("delFlag","0","companyDeptId",110L));
        assertEquals(2,service.addMappings(1L,input,9L).get("createdCount"));verify(mapper,times(2)).insertMapping(anyMap());verify(mapper).bumpVersion(1L);
    }
    @Test void bulkMappingRejectsDuplicateSelectionAndStaleVersion()
    {
        when(mapper.connection(1L)).thenReturn(connection());when(mapper.lockConnection(1L)).thenReturn(connection());
        when(provider.directory(anyString())).thenReturn(Arrays.asList(map("externalUserId","u1"),map("externalUserId","u2")));
        when(mapper.staff(7L)).thenReturn(map("delFlag","0","companyDeptId",110L));
        Map<String,Object> input=map("confirmed",true,"connectionVersion",2,"effectiveFrom","2026-09-01","items",Arrays.asList(map("userId",7L,"externalUserId","u1"),map("userId",7L,"externalUserId","u2")));
        assertThrows(ServiceException.class,()->service.addMappings(1L,input,9L));
        input.put("connectionVersion",1);assertThrows(ServiceException.class,()->service.addMappings(1L,input,9L));verify(mapper,never()).insertMapping(anyMap());
    }
    @Test void freshnessSupportsJdbcDatetimeAndTimestampWithoutHidingStaleRecords()
    {
        for (boolean stale : new boolean[] {false, true})
        {
            java.time.LocalDateTime seen = java.time.LocalDateTime.now().minusHours(stale ? 49 : 1);
            for (Object timestamp : new Object[] {seen, java.sql.Timestamp.valueOf(seen)})
            {
                Map<String,Object> source=map("lastSeenAt",timestamp,"quality","KNOWN");
                when(mapper.records(anyMap())).thenReturn(Arrays.asList(source));
                Map<String,Object> result=service.records(map("dateFrom","2026-09-01","dateTo","2026-09-07"),7L,false).get(0);
                assertEquals(stale ? "STALE" : "KNOWN",result.get("quality"));
            }
        }
    }
    @Test void employeeCanReadSelfButCannotChooseOtherPersonOrAllCompany()
    {
        when(mapper.records(anyMap())).thenReturn(new ArrayList<>());
        service.records(map("dateFrom","2026-09-01","dateTo","2026-09-07"),7L,false);
        ArgumentCaptor<Map<String,Object>> captured=ArgumentCaptor.forClass(Map.class);
        verify(mapper).records(captured.capture());assertEquals(7L,captured.getValue().get("userId"));
        assertThrows(ServiceException.class,()->service.records(map("dateFrom","2026-09-01","dateTo","2026-09-07","userId",8L),7L,false));
        assertThrows(ServiceException.class,()->service.records(map("dateFrom","2026-09-01","dateTo","2026-09-07","companyDeptId",110L),7L,false));
    }
    @Test void businessReadPermissionStillRequiresExplicitCompanyScope()
    {
        when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        Map<String,Object> query=map("companyDeptId",110L,"dateFrom","2026-09-01","dateTo","2026-09-07");
        assertThrows(ServiceException.class,()->service.records(query,7L,true));
        when(mapper.readerAllowed(110L,7L)).thenReturn(1);when(mapper.records(anyMap())).thenReturn(new ArrayList<>());
        service.records(query,7L,true);verify(mapper).records(anyMap());
    }
    @Test void ownerDirectoryContainsOnlyAuthorizedCompaniesAndSafeEmployeeFields()
    {
        Map<String,Object> owned=map("companyDeptId",110L,"companyName","公司A");
        when(mapper.companies()).thenReturn(Arrays.asList(owned,map("companyDeptId",111L,"companyName","公司B")));
        when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        when(mapper.company(111L)).thenReturn(map("leaderUserId",8L));
        when(mapper.people(110L)).thenReturn(Arrays.asList(map("userId",7L,"userName","员工A","departmentName","运营","loginName","private-login")));
        Map<String,Object> options=service.queryOptions(9L,true);
        assertEquals(Arrays.asList(owned),options.get("companies"));
        assertEquals(Arrays.asList(map("companyDeptId",110L,"userId",7L,"userName","员工A","departmentName","运营")),options.get("people"));
        verify(mapper,never()).people(111L);
    }
    @Test void selfOnlyEmployeeCannotEnumerateCoworkersEvenWhenCompanyLeader()
    {
        Map<String,Object> options=service.queryOptions(9L,false);
        assertEquals(Collections.emptyList(),options.get("companies"));
        assertEquals(Collections.emptyList(),options.get("people"));
        assertEquals(9L,options.get("currentUserId"));
        verifyNoInteractions(mapper);
    }
    @Test void administratorNeedsBusinessCompanyScopeToEnumerateEmployees()
    {
        when(mapper.companies()).thenReturn(Arrays.asList(map("companyDeptId",110L)));
        when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        assertEquals(Collections.emptyList(),service.queryOptions(1L,true).get("people"));
        verify(mapper,never()).people(anyLong());
        when(mapper.readerAllowed(110L,1L)).thenReturn(1);
        when(mapper.people(110L)).thenReturn(Arrays.asList(map("userId",7L,"userName","员工A")));
        assertEquals(1,((List<?>)service.queryOptions(1L,true).get("people")).size());
    }
    @Test void ownerCanSelectAllEmployeesOrOneEmployeeButCannotCrossCompany()
    {
        when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        when(mapper.company(111L)).thenReturn(map("leaderUserId",8L));
        Map<String,Object> query=map("companyDeptId",110L,"dateFrom","2026-09-01","dateTo","2026-09-07");
        service.records(query,9L,true);
        query.put("userId",7L);service.records(query,9L,true);
        ArgumentCaptor<Map<String,Object>> captured=ArgumentCaptor.forClass(Map.class);
        verify(mapper,times(2)).records(captured.capture());
        assertNull(captured.getAllValues().get(0).get("userId"));
        assertEquals(7L,captured.getAllValues().get(1).get("userId"));
        for(Map<String,Object> filter:captured.getAllValues()) assertEquals(110L,filter.get("companyDeptId"));
        query.put("companyDeptId",111L);
        assertThrows(ServiceException.class,()->service.records(query,9L,true));
        query.remove("companyDeptId");
        assertThrows(ServiceException.class,()->service.records(query,9L,true));
    }
    @Test void technicalAdministratorCannotSignCompanyAcceptanceOrCutover()
    {
        when(mapper.connection(1L)).thenReturn(connection()); when(mapper.lockConnection(1L)).thenReturn(connection());
        when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        assertThrows(ServiceException.class,()->service.cutoverStatus(1L,null,1L));
        assertThrows(ServiceException.class,()->service.validate(1L,map("caseType","NORMAL"),1L));
        verify(mapper,never()).insertValidation(anyMap());
    }
    @Test void laterCompleteReplayOmittingARecordKeepsHistoryButMarksItUncertain()
    {
        Map<String,Object> source=map("connectionId",1L,"userId",7L,"isCurrent",1,"lastSeenRunId",10L,"businessDate","2026-09-07","lastSeenAt",new java.util.Date(),"normalizedStatus","CONFIRMED","quality","KNOWN","sourceDurationSeconds",3600L);
        when(mapper.records(anyMap())).thenReturn(Arrays.asList(source));
        when(mapper.runs(1L)).thenReturn(Arrays.asList(map("runId",20L,"status","COMPLETE","windowStart","2026-09-07","windowEnd","2026-09-07")));
        Map<String,Object> result=service.records(map("dateFrom","2026-09-07","dateTo","2026-09-07"),7L,false).get(0);
        assertEquals("PARTIAL",result.get("quality"));assertEquals("NOT_SEEN_IN_LATEST_COMPLETE",result.get("qualityReason"));
        assertEquals("CONFIRMED",result.get("normalizedStatus"));assertEquals(3600L,result.get("sourceDurationSeconds"));
        verify(mapper,never()).supersedeObservation(anyLong());verify(mapper,never()).insertObservation(anyMap());
    }
    @Test void idempotentReplayCanBeAcceptedUsingItsActualLastSeenRun()
    {
        when(mapper.lockConnection(1L)).thenReturn(connection());when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        when(mapper.run(20L)).thenReturn(map("connectionId",1L,"runId",20L,"status","COMPLETE","mappingVersion",2));
        when(mapper.observation(33L)).thenReturn(map("connectionId",1L,"observationId",33L,"syncRunId",10L,"lastSeenRunId",20L,"kind","LEAVE","normalizedStatus","CONFIRMED"));
        service.validate(1L,map("caseType","LEAVE","runId",20L,"observationId",33L,"expectedResult","与批准来源一致","sourceEvidence","测试用只读来源引用","localResult","已核对样本","reason","幂等回放核验","passed",true),9L);
        verify(mapper).insertValidation(anyMap());
        assertThrows(ServiceException.class,()->service.validate(1L,map("caseType","LEAVE","runId",21L,"observationId",33L),9L));
    }
    @Test void cutoverRemainsBlockedWithoutRealSamplesLatestCoverageAndLegacyCleanup()
    {
        when(mapper.connection(1L)).thenReturn(connection());when(mapper.company(110L)).thenReturn(map("leaderUserId",9L));
        when(mapper.pendingLocalLeave(110L)).thenReturn(2);when(mapper.crossingLocalLeave(anyMap())).thenReturn(1);
        when(mapper.unmappedStaff(anyMap())).thenReturn(3);when(mapper.validations(1L)).thenReturn(Collections.emptyList());
        Map<String,Object> status=service.cutoverStatus(1L,null,9L);
        assertEquals(false,status.get("canActivate"));assertEquals(2,status.get("pendingLocalCount"));
        assertTrue(((List<?>)status.get("blockers")).size()>=6);
    }
    @Test void scheduledCutoverStopsOverlappingNewLeaveButKeepsOlderHistoryAvailableDuringOutage()
    {
        Map<String,Object> c=connection();c.put("state","ACTIVE");c.put("effectiveDate",Date.valueOf("2026-09-10"));
        when(mapper.connectionForCompany(110L)).thenReturn(c);
        service.requireLocalLeaveAllowed(110L,Date.valueOf("2026-09-01"),Date.valueOf("2026-09-09"));
        assertThrows(ServiceException.class,()->service.requireLocalLeaveAllowed(110L,Date.valueOf("2026-09-09"),Date.valueOf("2026-09-10")));
        assertEquals("FEISHU",service.getAuthority(110L,Date.valueOf("2026-09-10")).get("source"));
        verifyNoInteractions(provider); // A failed/disabled connection never reopens local authority.
    }
    @Test void mappingRefusesCrossCompanyAndOverlappingIdentityAssignments()
    {
        when(mapper.lockConnection(1L)).thenReturn(connection());
        when(mapper.staff(7L)).thenReturn(map("delFlag","0","companyDeptId",111L));
        Map<String,Object> input=map("userId",7L,"externalUserId","u1","effectiveFrom","2026-09-01");
        assertThrows(ServiceException.class,()->service.addMapping(1L,input,1L));
        when(mapper.staff(7L)).thenReturn(map("delFlag","0","companyDeptId",110L));when(mapper.mappingConflicts(anyMap())).thenReturn(1);
        assertThrows(ServiceException.class,()->service.addMapping(1L,input,1L));verify(mapper,never()).insertMapping(anyMap());
    }
    @Test void personAuthorityChecksBothCompaniesInStableLockOrderAndIncludesLeavers()
    {
        when(mapper.staff(7L)).thenReturn(map("userId",7L,"delFlag","1","employmentStatus","LEFT","companyDeptId",110L));
        Map<String,Object> active=connection();active.put("state","ACTIVE");active.put("effectiveDate",Date.valueOf("2026-09-10"));
        when(mapper.connectionForCompany(110L)).thenReturn(active);
        assertFalse((Boolean)service.getPersonAuthority(7L,111L,Date.valueOf("2026-09-10")).get("localLeaveAllowed"));
        clearInvocations(mapper);
        assertThrows(ServiceException.class,()->service.requireLocalLeaveForPerson(7L,111L,Date.valueOf("2026-09-10"),Date.valueOf("2026-09-11")));
        org.mockito.InOrder order=inOrder(mapper);order.verify(mapper).staff(7L);order.verify(mapper).lockCompany(110L);order.verify(mapper).lockCompany(111L);
        verifyNoInteractions(provider);
    }
    @Test void historicalObservationsPreventBackdatedIdentityRebinding()
    {
        when(mapper.mapping(5L)).thenReturn(map("mappingId",5L,"connectionId",1L,"status","CONFIRMED","effectiveFrom",Date.valueOf("2026-01-01")));
        when(mapper.lockConnection(1L)).thenReturn(connection());when(mapper.lastMappedDate(5L)).thenReturn("2026-09-07");
        assertThrows(ServiceException.class,()->service.retireMapping(5L,map("effectiveTo","2026-08-31","reason","转公司"),1L));
        verify(mapper,never()).retireMapping(anyMap());
    }
    @Test void syncUnconfiguredProviderDoesNotCreateSuccessOrCallNetwork()
    {
        when(mapper.connection(1L)).thenReturn(connection());
        assertThrows(ServiceException.class,()->service.sync(1L,map("windowStart","2026-09-01","windowEnd","2026-09-07"),1L));
        verify(mapper,never()).insertRun(anyMap());verify(provider,never()).query(anyString(),anyString(),anyString(),anyList(),any());
    }
    @Test void successfulReplayIsIdempotentAndPartialFailureDoesNotAdvanceCompleteWatermark()
    {
        Map<String,Object> c=connection();when(mapper.connection(1L)).thenReturn(c);
        Map<String,Object> running=connection();running.put("runningRunId",10L);
        when(mapper.lockConnection(1L)).thenReturn(c,running);
        when(provider.isConfigured("tenant")).thenReturn(true);
        when(mapper.mappings(1L)).thenReturn(Arrays.asList(map("mappingId",3L,"userId",7L,"externalUserId","u1","effectiveFrom",Date.valueOf("2026-01-01"))));
        doAnswer(inv->{((Map<String,Object>)inv.getArgument(0)).put("runId",10L);return 1;}).when(mapper).insertRun(anyMap());
        when(mapper.acquireRun(anyMap())).thenReturn(1);
        Map<String,Object> source=map("externalUserId","u1","sourceRecordKey","stable","kind","LEAVE","businessDate","2026-09-07","sourceTimezone","Asia/Shanghai","quality","KNOWN","normalizedStatus","CONFIRMED","sourceStatus","2","sourceDurationSeconds",3600L,"intervals",Collections.emptyList(),"sourceDetails",Collections.emptyMap(),"adapterVersion","v1");
        when(provider.query(eq("tenant"),anyString(),eq("APPROVAL"),anyList(),any())).thenReturn(Arrays.asList(source));
        when(provider.query(eq("tenant"),anyString(),eq("TASK"),anyList(),any())).thenThrow(new ServiceException("FEISHU_SCOPE_REJECTED"));
        when(mapper.currentObservation(anyMap())).thenAnswer(inv->{Map<String,Object> row=inv.getArgument(0);return map("observationId",11L,"userId",7L,"fingerprint",row.get("fingerprint"),"sourceRevision",1);});
        service.sync(1L,map("windowStart","2026-09-07","windowEnd","2026-09-07"),1L);
        verify(mapper).touchObservation(anyMap());verify(mapper,never()).insertObservation(anyMap());
        ArgumentCaptor<Map<String,Object>> finished=ArgumentCaptor.forClass(Map.class);verify(mapper).releaseRun(finished.capture());
        assertEquals("PARTIAL",finished.getValue().get("status"));assertEquals(1,finished.getValue().get("completedChunks"));
        assertEquals("FEISHU_SCOPE_REJECTED",finished.getValue().get("errorCode"));
    }
    @Test void changedSourceRereadCannotOverwriteExistingRevision()
    {
        Map<String,Object> c=connection();when(mapper.connection(1L)).thenReturn(c);
        Map<String,Object> running=connection();running.put("runningRunId",10L);when(mapper.lockConnection(1L)).thenReturn(c,running);
        when(provider.isConfigured("tenant")).thenReturn(true);when(mapper.acquireRun(anyMap())).thenReturn(1);
        doAnswer(inv->{((Map<String,Object>)inv.getArgument(0)).put("runId",10L);return 1;}).when(mapper).insertRun(anyMap());
        when(mapper.mappings(1L)).thenReturn(Arrays.asList(map("mappingId",3L,"userId",7L,"externalUserId","u1","effectiveFrom",Date.valueOf("2026-01-01"))));
        when(provider.query(anyString(),anyString(),anyString(),anyList(),any())).thenReturn(Arrays.asList(map("sourceRecordKey","old")),Arrays.asList(map("sourceRecordKey","new")));
        service.sync(1L,map("windowStart","2026-09-07","windowEnd","2026-09-07"),1L);
        verify(mapper,never()).supersedeObservation(anyLong());verify(mapper,never()).insertObservation(anyMap());
        ArgumentCaptor<Map<String,Object>> issue=ArgumentCaptor.forClass(Map.class);verify(mapper).insertIssue(issue.capture());
        assertEquals("FEISHU_SOURCE_CHANGED_DURING_READ",issue.getValue().get("issueCode"));
    }
    private Map<String,Object> connection()
    { return map("connectionId",1L,"companyDeptId",110L,"tenantKey","tenant","timezone","Asia/Shanghai","state","PARALLEL","version",2); }
}
