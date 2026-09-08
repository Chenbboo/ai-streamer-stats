package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectWorkServiceTest
{
    @Mock BusinessProjectWorkMapper mapper;
    @Mock BusinessProjectMapper projectMapper;
    @Spy ObjectMapper json=new ObjectMapper();
    @InjectMocks BusinessProjectWorkService service;
    BusinessProject project;
    Map<String,Object> calendar,unit,entry;

    @BeforeEach void setup()
    {
        project=new BusinessProject();project.setProjectId(1L);project.setMainOwnerUserId(10L);project.setSponsorOwnerUserId(20L);project.setCompanyDeptId(100L);project.setVersion(2);project.setBaselineVersion(1);
        project.setStatus("ACTIVE");project.setDeliveryPolicyVersion("SEPARATED_V1");project.setAccountingState("OPEN");project.setCostPolicyVersion("ACTUAL_WORK_V1");project.setTemplateVersion("LIGHT_V1");
        project.setPlanStartDate(Date.valueOf("2026-01-01"));project.setPlanEndDate(Date.valueOf("2026-12-31"));project.setActualStartDate(Date.valueOf("2026-01-01"));
        calendar=row("calendarId",1L,"timeZone","Asia/Shanghai","workingWeekdays","1,2,3,4,5","dailyMinutes",360,"effectiveFrom",Date.valueOf("2000-01-01"),"exceptionsJson","[]");
        unit=row("unitPolicyId",1L,"minutesPerDay",480,"effectiveFrom",Date.valueOf("2000-01-01"));
        lenient().when(projectMapper.selectProjectByIdForUpdate(1L)).thenReturn(project);lenient().when(projectMapper.selectProjectById(1L)).thenReturn(project);
        lenient().when(mapper.selectCalendar(1L)).thenReturn(calendar);lenient().when(mapper.selectUnitPolicy(1L)).thenReturn(unit);
        lenient().when(mapper.selectEntryBySource(anyLong(),anyString())).thenReturn(null);
        lenient().when(mapper.countMembership(eq(1L),anyLong(),anyString())).thenReturn(1);
        lenient().doAnswer(call->{Map<String,Object> v=call.getArgument(0);v.put("entryId",101L);entry=new HashMap<String,Object>(v);entry.put("createUserId",v.get("actorId"));entry.put("status","DRAFT");entry.put("version",0);entry.put("isCurrent","0");return 1;}).when(mapper).insertEntry(anyMap());
        lenient().when(mapper.selectEntry(101L)).thenAnswer(call->entry);lenient().when(mapper.selectEntryForUpdate(101L)).thenAnswer(call->entry);
        lenient().doAnswer(call->{Map<String,Object> v=call.getArgument(0);entry.put("status",v.get("toStatus"));entry.put("isCurrent",v.get("isCurrent"));entry.put("logicalEntryId",101L);entry.put("version",((Number)entry.get("version")).intValue()+1);return 1;}).when(mapper).transitionEntry(anyMap());
    }

    @Test void workUnitUsesItsOwnSnapshotInsteadOfCalendarCapacity()
    {
        Map<String,Object> result=service.saveEntry(1L,input("0.5"),30L,"member");
        assertEquals(240,result.get("workMinutes"));assertEquals(480,result.get("minutesPerDay"));assertEquals(360,result.get("capacityMinutes"));assertEquals("DRAFT",result.get("status"));
        verify(mapper,never()).insertEvent(anyMap());assertFalse(result.containsKey("unitCost"));
    }
    @Test void fractionalMinutesAreRejectedInsteadOfRounded(){assertThrows(ServiceException.class,()->BusinessProjectWorkService.minutes(new BigDecimal("0.001"),"HOUR",480));}
    @Test void noRecordedActualIsNotFilledFromResourcePlan(){when(mapper.selectMembers(1L)).thenReturn(Collections.singletonList(row("userId",30L)));Map<String,Object> result=service.workspace(1L,Collections.emptyMap(),30L,false);assertEquals(Collections.emptyList(),result.get("entries"));assertEquals("UNKNOWN",result.get("missingActualMeaning"));}
    @Test void ownerCannotConfirmOwnWorkButSponsorCan()
    {
        service.saveEntry(1L,input("0.5"),10L,"owner");service.transition(101L,"submit",row("version",0),10L,"owner");
        assertThrows(ServiceException.class,()->service.transition(101L,"confirm",row("version",1),10L,"owner"));
        assertEquals("CONFIRMED",service.transition(101L,"confirm",row("version",1),20L,"sponsor").get("status"));verify(mapper).insertEvent(anyMap());
    }
    @Test void ownerProxyCannotConfirmTheirOwnProxySubmission()
    {
        Map<String,Object> body=input("0.5");body.put("userId",30L);service.saveEntry(1L,body,10L,"owner");service.transition(101L,"submit",row("version",0),10L,"owner");
        assertThrows(ServiceException.class,()->service.transition(101L,"confirm",row("version",1),10L,"owner"));
        assertEquals("CONFIRMED",service.transition(101L,"confirm",row("version",1),20L,"sponsor").get("status"));
    }
    @Test void unrelatedAdministratorIdCannotApproveBusinessWork(){service.saveEntry(1L,input("0.5"),30L,"member");service.transition(101L,"submit",row("version",0),30L,"member");assertThrows(ServiceException.class,()->service.transition(101L,"confirm",row("version",1),1L,"admin"));verify(mapper,never()).insertEvent(anyMap());}
    @Test void confirmedWorkProducesOnlyOneEventAndRejectsStaleRetry(){service.saveEntry(1L,input("0.5"),30L,"member");service.transition(101L,"submit",row("version",0),30L,"member");service.transition(101L,"confirm",row("version",1),10L,"owner");assertThrows(ServiceException.class,()->service.transition(101L,"confirm",row("version",1),10L,"owner"));verify(mapper,times(1)).insertEvent(anyMap());}
    @Test void crossProjectDailyLimitIsRecheckedDuringConfirmation(){service.saveEntry(1L,input("0.5"),30L,"member");service.transition(101L,"submit",row("version",0),30L,"member");when(mapper.sumReservedMinutes(30L,"2026-03-02",101L)).thenReturn(1300);assertThrows(ServiceException.class,()->service.transition(101L,"confirm",row("version",1),10L,"owner"));verify(mapper,never()).insertEvent(anyMap());}
    @Test void lateActualIsAllowedOnlyInsideExecutionPeriodAndOpenLedger(){project.setStatus("CLOSED");project.setActualEndDate(Date.valueOf("2026-03-03"));assertEquals("DRAFT",service.saveEntry(1L,input("0.5"),30L,"member").get("status"));Map<String,Object> late=input("1");late.put("bizDate","2026-03-04");assertThrows(ServiceException.class,()->service.saveEntry(1L,late,30L,"member"));project.setAccountingState("CLOSED");assertThrows(ServiceException.class,()->service.saveEntry(1L,input("0.5"),30L,"member"));}
    @Test void daylightSavingDayUsesRealDayLength(){calendar.put("timeZone","America/New_York");Map<String,Object> body=input("3");body.put("bizDate","2026-03-08");assertThrows(ServiceException.class,()->service.saveEntry(1L,body,30L,"member"));verify(mapper,never()).insertEntry(anyMap());}
    @Test void correctionDoesNotSupersedeValidWorkUntilApproved()
    {
        Map<String,Object> original=row("entryId",50L,"logicalEntryId",50L,"projectId",1L,"userId",30L,"bizDate",Date.valueOf("2026-03-02"),"status","CONFIRMED","isCurrent","1","version",2,"revisionNo",1,"createUserId",30L,"calendarId",1L,"unitPolicyId",1L,"activity","结果整理");
        when(mapper.selectEntry(50L)).thenReturn(original);when(mapper.selectEntryForUpdate(50L)).thenReturn(original);
        Map<String,Object> corrected=service.correct(50L,row("version",2,"inputQuantity","0","inputUnit","DAY","unitPolicyId",1L,"reason","原记录重复，申请撤销"),30L,"member");
        assertEquals(0,corrected.get("workMinutes"));assertEquals("CONFIRMED",original.get("status"));verify(mapper,never()).supersedeEntry(anyLong(),anyInt(),anyString());
        service.transition(101L,"submit",row("version",0),30L,"member");when(mapper.supersedeEntry(50L,2,"owner")).thenReturn(1);service.transition(101L,"confirm",row("version",1),10L,"owner");verify(mapper).supersedeEntry(50L,2,"owner");
    }
    @Test void calendarHolidayAndUnitAreIndependentForPlanning(){calendar.put("exceptionsJson","[{\"bizDate\":\"2026-03-03\",\"minutes\":0}]");doAnswer(call->{((Map<String,Object>)call.getArgument(0)).put("assignmentId",55L);return 1;}).when(mapper).insertAssignment(anyMap());Map<String,Object> body=row("userId",30L,"effectiveFrom","2026-03-02","effectiveTo","2026-03-03","inputUnit","DAY","inputQuantity","0.5","calendarId",1L,"unitPolicyId",1L);Map<String,Object> result=service.saveAssignment(1L,body,10L,"owner");assertEquals(240,result.get("plannedMinutes"));ArgumentCaptor<Map<String,Object>> days=ArgumentCaptor.forClass(Map.class);verify(mapper).insertAllocationDay(days.capture());assertEquals("2026-03-02",days.getValue().get("bizDate"));}
    @Test void duplicateSourceCannotRevealOtherPersonsRecord(){when(mapper.selectEntryBySource(1L,"request-1")).thenReturn(row("createUserId",40L,"userId",40L));assertThrows(ServiceException.class,()->service.saveEntry(1L,input("1"),30L,"member"));}
    @Test void unlimitedProjectSupportsBoundedStaffParticipation()
    {
        project.setPlanEndDate(null);
        Map<String,Object> result=service.saveAssignment(1L,row("userId",30L,"effectiveFrom","2027-03-01","effectiveTo","2027-03-02",
            "inputUnit","HOUR","inputQuantity",2,"calendarId",1L,"unitPolicyId",1L),10L,"owner");
        assertEquals(120,result.get("plannedMinutes"));assertEquals("2027-03-02",result.get("effectiveTo"));
        verify(mapper).insertAssignment(anyMap());
    }
    @Test void unlimitedParticipationStoresNoEndAndPlansOnlyCurrentBudgetPeriod()
    {
        project.setPlanEndDate(null);project.setTemplateSnapshotJson("{\"budget\":{\"cycle\":\"MONTH\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-31\"}}");
        Map<String,Object> result=service.saveAssignment(1L,row("userId",30L,"effectiveFrom","2026-03-01","effectiveTo",null,
            "participationOnly",true,"calendarId",1L,"unitPolicyId",1L),10L,"owner");
        assertNull(result.get("effectiveTo"));assertEquals(0,result.get("plannedMinutes"));verify(mapper,never()).insertAllocationDay(anyMap());
    }
    @Test void finiteProjectRejectsUnlimitedParticipation()
    {assertThrows(ServiceException.class,()->service.saveAssignment(1L,row("userId",30L,"effectiveFrom","2026-03-01","effectiveTo",null,"inputUnit","PERCENTAGE","inputQuantity",100,"calendarId",1L,"unitPolicyId",1L),10L,"owner"));}
    private Map<String,Object> input(String quantity){return row("bizDate","2026-03-02","inputUnit","DAY","inputQuantity",quantity,"unitPolicyId",1L,"calendarId",1L,"activity","交付结果整理","reason","未计划工作补录说明","sourceKey","request-1");}
    static Map<String,Object> row(Object... pairs){Map<String,Object> map=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)map.put(String.valueOf(pairs[i]),pairs[i+1]);return map;}
}
