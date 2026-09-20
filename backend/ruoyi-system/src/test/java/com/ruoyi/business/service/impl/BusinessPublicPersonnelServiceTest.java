package com.ruoyi.business.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessPublicPersonnelServiceTest {
    BusinessPublicPersonnelService service=new BusinessPublicPersonnelService();
    BusinessPublicExpenseMapper mapper=mock(BusinessPublicExpenseMapper.class);
    BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
    BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"projects",projects);
        ReflectionTestUtils.setField(service,"work",work);
        when(mapper.selectPersonnelStaff(10L,"2025-02")).thenAnswer(call->new ArrayList<>(Arrays.asList(row("userId",2L,"userName","人事","deptId",11L,"deptName","人事部"))));
        when(work.selectCalendars()).thenReturn(Arrays.asList(row("calendarId",1L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480)));
        when(work.selectBudgetRates(2L,"2025-02-01","2025-02-28")).thenReturn(Arrays.asList(row("costMode","MONTHLY","unitCost",bd("10000.00"),"currency","CNY")));
    }
    Map<String,Object> edit(){return row("userId",2L,"totalAmount",bd("10000.00"),"reason","","businessFactIds",new ArrayList<>());}
    @Test void hireMonthUsesTheSameMonthlyAmountAsInternalRate(){
        when(mapper.selectPersonnelStaff(10L,"2025-02")).thenReturn(Arrays.asList(
            row("userId",2L,"userName","新入职员工","deptId",11L,"deptName","人事部","hireDate","2025-02-10")));
        assertEquals(bd("10000.00"),previewPerson().get("totalAmount"));
        Map<String,Object> result=service.automaticSnapshot(10L,"2025-02","CNY",null);
        assertEquals(bd("10000.00"),result.get("totalAmount"));
        assertEquals(bd("10000.00"),result.get("publicAmount"));
    }
    @Test void monthlyCostTimesProjectAllocationKeepsOnlyResidual(){
        project(false);
        Map<String,Object> result=service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit()));
        assertEquals(bd("10000.00"),result.get("totalAmount"));assertEquals(bd("6000.00"),result.get("publicAmount"));assertEquals(bd("275.86"),result.get("dailyReference"));
    }
    @SuppressWarnings("unchecked")
    @Test void twoProjectPercentagesProduceTheAmountsInTheExample(){
        when(work.selectBudgetRates(2L,"2025-02-01","2025-02-28")).thenReturn(Arrays.asList(
            row("costMode","MONTHLY","unitCost",bd("11250.00"),"currency","CNY")));
        for(long projectId:new long[]{3L,4L}) {
            BusinessProject project=new BusinessProject();project.setProjectId(projectId);
            project.setProjectName(projectId==3L?"管理系统":"算命系统");project.setBaseCurrency("CNY");
            when(projects.selectProjectById(projectId)).thenReturn(project);
        }
        when(projects.selectUserAllocationTimeline(2L)).thenReturn(Arrays.asList(
            row("projectId",3L,"allocationId",30L,"allocationValue",bd("60.00"),"effectiveFrom","2025-02-01"),
            row("projectId",4L,"allocationId",40L,"allocationValue",bd("40.00"),"effectiveFrom","2025-02-01")));
        Map<String,Object> person=previewPerson();
        assertEquals(bd("11250.00"),person.get("projectAmount"));
        List<Map<String,Object>> details=(List<Map<String,Object>>)person.get("projectAllocations");
        assertEquals(bd("6750.00"),details.get(0).get("amount"));
        assertEquals(bd("4500.00"),details.get(1).get("amount"));
    }
    @SuppressWarnings("unchecked")
    @Test void fourProjectsAtTwentyFivePercentEachConserveMonthlyCost(){
        when(work.selectBudgetRates(2L,"2025-02-01","2025-02-28")).thenReturn(Arrays.asList(
            row("costMode","MONTHLY","unitCost",bd("9375.00"),"currency","CNY")));
        List<Map<String,Object>> allocations=new ArrayList<>();
        for(long projectId=3L;projectId<=6L;projectId++) {
            BusinessProject project=new BusinessProject();project.setProjectId(projectId);
            project.setProjectName("项目"+projectId);project.setBaseCurrency("CNY");
            when(projects.selectProjectById(projectId)).thenReturn(project);
            allocations.add(row("projectId",projectId,"allocationId",projectId*10,
                "allocationValue",bd("25.00"),"effectiveFrom","2025-02-01"));
        }
        when(projects.selectUserAllocationTimeline(2L)).thenReturn(allocations);
        Map<String,Object> person=previewPerson();
        assertEquals(bd("9375.00"),person.get("projectAmount"));
        for(Map<String,Object> detail:(List<Map<String,Object>>)person.get("projectAllocations"))
            assertEquals(bd("2343.75"),detail.get("amount"));
    }
    @Test void completedProjectAllocationStillAppliesToItsFinalMonth(){
        project(true);
        assertEquals(bd("0.00"),service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit())).get("publicAmount"));
    }
    @Test void pendingAllocationCannotBePassedOffAsZero(){
        project(false);
        when(projects.selectUserAllocationTimeline(2L)).thenReturn(Arrays.asList(
            row("projectId",3L,"allocationId",30L,"allocationValue",bd("40.00"),"effectiveFrom","2025-02-01","confirmationStatus","PENDING")));
        assertThrows(ServiceException.class,()->service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit())));
    }
    @Test void linkedOutsourcingExpenseDeductedAndDuplicateLinksRejected(){
        when(mapper.selectPersonnelBusinessFacts(10L,"2025-02","CNY")).thenReturn(Arrays.asList(row("factId",7L,"amount",bd("3000.00"))));
        Map<String,Object> edit=edit();edit.put("businessFactIds",Arrays.asList(7L));edit.put("reason","外包人员当月付款流水");
        assertEquals(bd("7000.00"),service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit)).get("publicAmount"));
        edit.put("businessFactIds",Arrays.asList(7L,7L));assertThrows(ServiceException.class,()->service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit)));
        edit.put("businessFactIds",Arrays.asList(999L));assertThrows(ServiceException.class,()->service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit)));
    }
    @Test void missingRateNeedsActualPayrollWithEvidence(){
        when(work.selectBudgetRates(anyLong(),anyString(),anyString())).thenReturn(Collections.emptyList());
        Map<String,Object> edit=edit();assertThrows(ServiceException.class,()->service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit)));
        edit.put("reason","经核实的二月工资表");assertEquals(bd("10000.00"),service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit)).get("publicAmount"));
    }
    @Test void confirmedSnapshotMustRemainConsistentAtSettlement(){
        Map<String,Object> saved=service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit()));
        Map<String,Object> bill=row("companyDeptId",10L,"month","2025-02","currency","CNY","personnelSnapshot",JSON.toJSONString(saved));
        assertDoesNotThrow(()->service.validateSettlement(bill));
        project(false);
        assertThrows(ServiceException.class,()->service.validateSettlement(bill));
    }
    @Test void estimatesUse2175ButSettlementConservesMonthIncludingShortProjects(){
        for(int days:new int[]{4,28,30,31}){
            LocalDate start=LocalDate.of(2025,1,1),end=start.plusDays(days-1);
            List<Map<String,Object>> daily=BusinessPublicExpenseDailyService.combinedSchedule(1L,2L,bd("2475.00"),bd("2175.00"),start,end,false);
            assertEquals(bd("100.00").add(bd("300.00").divide(new BigDecimal(days),2,java.math.RoundingMode.DOWN)),daily.get(0).get("amount"));
            List<Map<String,Object>> settled=BusinessPublicExpenseDailyService.combinedSchedule(1L,2L,bd("2475.00"),bd("2175.00"),start,end,true);
            assertEquals(bd("2475.00"),settled.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
        }
    }
    @Test void directAmountsCannotExceedPayroll(){assertThrows(ServiceException.class,()->BusinessPublicPersonnelService.remainder(bd("100"),bd("80"),bd("30")));}
    @SuppressWarnings("unchecked")
    Map<String,Object> previewPerson(){return ((List<Map<String,Object>>)service.preview(10L,"2025-02","CNY").get("rows")).get(0);}
    @Test void missingWholeMonthAndPartialCoverageHaveAccurateDates(){
        when(work.selectBudgetRates(anyLong(),anyString(),anyString())).thenReturn(Collections.emptyList());
        Map<String,Object> person=previewPerson();
        assertEquals(Arrays.asList("本月未设置人员成本"),person.get("issues"));
        assertNull(person.get("totalAmount"));
        assertEquals(20,((List<?>)((Map<?,?>)((List<?>)person.get("issueDetails")).get(0)).get("dates")).size());
        when(work.selectBudgetRates(anyLong(),anyString(),anyString())).thenReturn(Arrays.asList(row("costMode","MONTHLY","unitCost",bd("10000.00"),"currency","CNY","effectiveFrom","2025-02-04")));
        person=previewPerson();assertEquals(Arrays.asList("部分工作日缺少有效人员成本"),person.get("issues"));
        assertEquals(Arrays.asList("2025-02-03"),((Map<?,?>)((List<?>)person.get("issueDetails")).get(0)).get("dates"));
    }
    @Test void pendingProjectAllocationShowsProjectAndCause(){
        project(false);projects.selectProjectById(3L).setProjectName("3");
        when(projects.selectUserAllocationTimeline(2L)).thenReturn(Arrays.asList(
            row("projectId",3L,"allocationId",30L,"allocationValue",bd("40.00"),"effectiveFrom","2025-02-01","confirmationStatus","PENDING")));
        Map<String,Object> person=previewPerson();
        assertNotNull(person.get("totalAmount"));
        assertNull(person.get("projectAmount"));
        List<?> details=(List<?>)person.get("projectIssueDetails");assertEquals(1,details.size());
        Map<?,?> first=(Map<?,?>)details.get(0);assertEquals("3",first.get("projectName"));assertEquals(3L,first.get("projectId"));
        assertEquals("项目投入比例待负责人确认",first.get("reason"));
        assertTrue(((List<?>)person.get("projectIssues")).get(0).toString().startsWith("项目「3」"));
        assertThrows(ServiceException.class,()->service.snapshot(10L,"2025-02","CNY",Arrays.asList(edit())));
    }
    @SuppressWarnings("unchecked")
    @Test void diagnosticUpgradeDoesNotInvalidateExistingFinancialSnapshot(){
        when(work.selectBudgetRates(anyLong(),anyString(),anyString())).thenReturn(Collections.emptyList());
        Map<String,Object> input=edit();input.put("reason","工资表核实");
        Map<String,Object> saved=service.snapshot(10L,"2025-02","CNY",Arrays.asList(input));
        Map<String,Object> person=((List<Map<String,Object>>)saved.get("rows")).get(0);
        person.remove("issueDetails");person.remove("projectIssueDetails");person.put("issues",Arrays.asList("缺少部分日期的人员成本"));
        assertDoesNotThrow(()->service.validateSettlement(row("companyDeptId",10L,"month","2025-02","currency","CNY","personnelSnapshot",JSON.toJSONString(saved))));
    }
    @Test void automaticSnapshotUsesPoliciesAndDeductsProjectCosts() {
        project(false);
        Map<String,Object> old=row("rows",Arrays.asList(row("userId",2L,"totalAmount",bd("99999.00"),"businessFactIds",Collections.emptyList())));
        Map<String,Object> saved=service.automaticSnapshot(10L,"2025-02","CNY",old);
        assertEquals("AUTOMATIC",saved.get("sourceMode"));assertEquals(bd("10000.00"),saved.get("totalAmount"));assertEquals(bd("6000.00"),saved.get("publicAmount"));
        Map<String,Object> bill=row("companyDeptId",10L,"month","2025-02","currency","CNY","personnelSnapshot",JSON.toJSONString(saved));
        assertDoesNotThrow(()->service.validateSettlement(bill));
        when(work.selectBudgetRates(anyLong(),anyString(),anyString())).thenReturn(Arrays.asList(row("costMode","MONTHLY","unitCost",bd("12000.00"),"currency","CNY")));
        assertThrows(ServiceException.class,()->service.validateSettlement(bill));
    }
    @Test void automaticSnapshotRejectsMissingMonthlyCoverageAndPendingAllocation() {
        when(work.selectBudgetRates(anyLong(),anyString(),anyString())).thenReturn(Arrays.asList(row("costMode","MONTHLY","unitCost",bd("10000.00"),"currency","CNY","effectiveFrom","2025-02-04")));
        assertTrue(assertThrows(ServiceException.class,()->service.automaticSnapshot(10L,"2025-02","CNY",null)).getMessage().contains("人员成本设置"));
        setup();project(false);when(projects.selectUserAllocationTimeline(2L)).thenReturn(Arrays.asList(
            row("projectId",3L,"allocationId",30L,"allocationValue",bd("40.00"),"effectiveFrom","2025-02-01","confirmationStatus","PENDING")));
        assertThrows(ServiceException.class,()->service.automaticSnapshot(10L,"2025-02","CNY",null));
    }
    @Test void automaticRefreshPreservesHistoricalDeductionWithoutNewUserInput() {
        when(mapper.selectPersonnelBusinessFacts(10L,"2025-02","CNY")).thenReturn(Arrays.asList(row("factId",7L,"amount",bd("3000.00"))));
        Map<String,Object> old=row("rows",Arrays.asList(row("userId",2L,"businessFactIds",Arrays.asList(7L),"reason","原外包付款")));
        Map<String,Object> saved=service.automaticSnapshot(10L,"2025-02","CNY",old);
        assertEquals(bd("7000.00"),saved.get("publicAmount"));
        assertDoesNotThrow(()->service.validateSettlement(row("companyDeptId",10L,"month","2025-02","currency","CNY","personnelSnapshot",JSON.toJSONString(saved))));
        when(mapper.selectPersonnelBusinessFacts(anyLong(),anyString(),anyString())).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class,()->service.automaticSnapshot(10L,"2025-02","CNY",old));
    }
    void project(boolean closed){
        BusinessProject p=new BusinessProject();p.setProjectId(3L);p.setProjectName("项目");p.setCostPolicyVersion("MEMBER_DAYS_V1");p.setBaseCurrency("CNY");p.setAccountingState(closed?"CLOSED":"OPEN");when(projects.selectProjectById(3L)).thenReturn(p);
        when(projects.selectUserAllocationTimeline(2L)).thenReturn(Arrays.asList(row("projectId",3L,"allocationId",30L,
            "allocationValue",bd(closed?"100.00":"40.00"),"effectiveFrom","2025-02-01","projectEndDate","2025-02-28")));
    }
    static BigDecimal bd(String s){return new BigDecimal(s);}
    static Map<String,Object> row(Object...pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
}
