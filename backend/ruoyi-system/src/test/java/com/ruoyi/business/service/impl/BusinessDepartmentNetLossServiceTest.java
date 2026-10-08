package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessDepartmentNetLossServiceTest {
    BusinessPublicPersonnelService service=new BusinessPublicPersonnelService();
    BusinessPublicExpenseMapper mapper=mock(BusinessPublicExpenseMapper.class);
    BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
    BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"projects",projects);ReflectionTestUtils.setField(service,"work",work);
        when(mapper.selectPersonnelStaff(110L,"2025-02")).thenReturn(Arrays.asList(
            row("userId",1L,"userName","运营","publicCostExcluded",1),row("userId",2L,"userName","商务","publicCostExcluded",true),
            row("userId",3L,"userName","AI","publicCostExcluded",1),row("userId",4L,"userName","IT","publicCostExcluded",1,"itPayroll",1)));
        when(mapper.selectItLossProjects(110L,"2025-02","CNY")).thenReturn(Arrays.asList(it(9L,"-10000.00"),it(16L,"3000.00")));
    }
    Map<String,Object> snapshot(){return service.allocationSnapshot(110L,"2025-02","CNY",null);}
    @Test void directPersonnelPreviewNormalizesOnlyParticipatingProjectsAndBlocksMissingWeights(){
        when(mapper.selectPersonnelStaff(110L,"2026-09")).thenReturn(Collections.singletonList(row("userId",139L,"userName","苏正强","publicCostExcluded",1)));
        when(work.selectCalendars()).thenReturn(Collections.singletonList(row("calendarId",1L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480)));
        when(work.selectBudgetRates(139L,"2026-09-01","2026-09-30")).thenReturn(Collections.singletonList(row("costMode","MONTHLY","unitCost",bd("11250.00"),"currency","CNY")));
        BusinessProject project=new BusinessProject();project.setProjectId(20L);project.setProjectName("DEWI");project.setBaseCurrency("CNY");when(projects.selectProjectById(20L)).thenReturn(project);
        when(projects.selectUserAllocationTimeline(139L)).thenReturn(Collections.singletonList(row("projectId",20L,"allocationValue",20,"confirmationStatus","CONFIRMED","projectCurrency","CNY","effectiveFrom","2026-09-23")));
        Map<String,Object> result=service.allocationSnapshot(110L,"2026-09","CNY",null);
        Map<?,?> person=(Map<?,?>)((List<?>)result.get("personnelDetails")).get(0);
        assertEquals(bd("11250.00"),person.get("projectAmount"));assertEquals(bd("100.00"),person.get("projectAllocationPercent"));assertEquals(BigDecimal.ZERO,person.get("publicAmount"));
        BusinessMemberDayCostService days=mock(BusinessMemberDayCostService.class);ReflectionTestUtils.setField(service,"memberDays",days);
        when(days.fullMonthlyPayroll(139L,java.time.YearMonth.of(2026,9),"CNY")).thenReturn(Collections.singletonList(row("projectId",20L,"allocationPercent",bd("100.00"),"amount",bd("11250.00"))));
        assertDoesNotThrow(()->service.allocationSnapshot(110L,"2026-09","CNY",null));
        when(days.fullMonthlyPayroll(139L,java.time.YearMonth.of(2026,9),"CNY")).thenReturn(Collections.singletonList(row("projectId",20L,"allocationPercent",bd("20.00"),"amount",bd("2250.00"))));
        assertThrows(ServiceException.class,()->service.allocationSnapshot(110L,"2026-09","CNY",null));
        when(days.fullMonthlyPayroll(139L,java.time.YearMonth.of(2026,9),"CNY")).thenReturn(Arrays.asList(
            row("projectId",17L,"projectName","唐勃珠宝","projectDeleted",true,"allocationPercent",bd("1.52"),"amount",bd("170.46")),
            row("projectId",20L,"allocationPercent",bd("98.48"),"amount",bd("11079.54"))));
        Map<String,Object> named=service.allocationSnapshot(110L,"2026-09","CNY",null);
        Map<?,?> archived=(Map<?,?>)((List<?>)((Map<?,?>)((List<?>)named.get("personnelDetails")).get(0)).get("projectAllocations")).get(0);
        assertEquals("唐勃珠宝",archived.get("projectName"));assertEquals(true,archived.get("projectDeleted"));assertEquals(bd("170.46"),archived.get("amount"));
        ReflectionTestUtils.setField(service,"memberDays",null);
        when(projects.selectUserAllocationTimeline(139L)).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class,()->service.allocationSnapshot(110L,"2026-09","CNY",null));
    }
    @Test void supportPreviewUsesTheSameMonthlyProjectAmountsAndOnlyPublishesTheRemainder(){
        when(mapper.selectPersonnelStaff(110L,"2026-09")).thenReturn(Collections.singletonList(row("userId",125L,"userName","公司人员")));
        when(work.selectCalendars()).thenReturn(Collections.singletonList(row("calendarId",1L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480)));
        when(work.selectBudgetRates(125L,"2026-09-01","2026-09-30")).thenReturn(Collections.singletonList(row("costMode","MONTHLY","unitCost",bd("7500.00"),"currency","CNY")));
        BusinessMemberDayCostService days=mock(BusinessMemberDayCostService.class);ReflectionTestUtils.setField(service,"memberDays",days);
        when(days.fullMonthlyPayroll(125L,java.time.YearMonth.of(2026,9),"CNY")).thenReturn(Collections.singletonList(row("projectId",20L,"projectName","DEWI","allocationPercent",bd("20.00"),"amount",bd("1500.00"))));
        Map<String,Object> result=service.allocationSnapshot(110L,"2026-09","CNY",null);
        assertEquals(bd("6000.00"),result.get("staffPublicAmount"));assertEquals(bd("1500.00"),((Map<?,?>)((List<?>)result.get("personnelDetails")).get(0)).get("projectAmount"));
    }
    @Test void offsetsItProfitAndExcludesBusinessAndItPayroll(){
        Map<String,Object> result=snapshot();
        assertEquals("DEPARTMENT_NET_V1",result.get("sourceMode"));assertTrue(((List<?>)result.get("rows")).isEmpty());
        assertEquals(bd("7000.00"),result.get("publicAmount"));assertEquals(bd("0"),result.get("staffPublicAmount"));
        assertFalse(result.containsKey("dailyReference"));verify(work,never()).selectBudgetRates(eq(4L),anyString(),anyString());
        List<Map<String,Object>> details=(List<Map<String,Object>>)result.get("personnelDetails");
        assertEquals(3,details.size());
        for(Map<String,Object> person:details){assertEquals(true,person.get("directProjectCost"));assertEquals(BigDecimal.ZERO,person.get("publicAmount"));}
        Map<?,?> loss=(Map<?,?>)result.get("itLoss");assertEquals(bd("3000.00"),loss.get("profitOffset"));
        assertEquals(bd("7000.00"),((Map<?,?>)((List<?>)loss.get("transfers")).get(0)).get("amount"));
    }
    @Test void netProfitDoesNotGenerateNegativeOrAdditionalPublicCost(){
        when(mapper.selectItLossProjects(110L,"2025-02","CNY")).thenReturn(Arrays.asList(it(9L,"-1000.00"),it(16L,"3000.00")));
        assertEquals(bd("0.00"),snapshot().get("publicAmount"));
    }
    @Test void retainsOtherPublicPayrollOnce(){
        when(mapper.selectPersonnelStaff(110L,"2025-02")).thenReturn(Arrays.asList(row("userId",5L,"userName","人事")));
        when(work.selectCalendars()).thenReturn(Arrays.asList(row("calendarId",1L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480)));
        when(work.selectBudgetRates(5L,"2025-02-01","2025-02-28")).thenReturn(Arrays.asList(row("costMode","MONTHLY","unitCost",bd("2000.00"),"currency","CNY")));
        Map<String,Object> result=snapshot();assertEquals(bd("9000.00"),result.get("publicAmount"));assertEquals(bd("2000.00"),result.get("staffPublicAmount"));
    }
    @Test void missingResultsPendingCostsAndUnconfirmedFactsBlockSaving(){
        for(String field:Arrays.asList("pendingCostCount","missingResultCount","unfinishedFactCount","profitAmount")) {
            Map<String,Object> item=it(9L,"-10000.00");item.put(field,"profitAmount".equals(field)?null:1);
            when(mapper.selectItLossProjects(110L,"2025-02","CNY")).thenReturn(Arrays.asList(item));
            assertThrows(ServiceException.class,this::snapshot,field);
        }
    }
    @Test void closedOrDeletedDeficitsCannotBeTransferred(){
        for(String field:Arrays.asList("accountingState","delFlag")) {
            Map<String,Object> item=it(9L,"-10000.00");item.put(field,"accountingState".equals(field)?"CLOSED":"2");
            when(mapper.selectItLossProjects(110L,"2025-02","CNY")).thenReturn(Arrays.asList(item));
            assertThrows(ServiceException.class,this::snapshot);
        }
    }
    @Test void sourceChangeRequiresExplicitRefreshBeforeSettlement(){
        Map<String,Object> saved=snapshot();Map<String,Object> bill=row("companyDeptId",110L,"month","2025-02","currency","CNY","personnelSnapshot",JSON.toJSONString(saved));
        assertDoesNotThrow(()->service.validateSettlement(bill));
        when(mapper.selectItLossProjects(110L,"2025-02","CNY")).thenReturn(Arrays.asList(it(9L,"-10000.00"),it(16L,"4000.00")));
        assertThrows(ServiceException.class,()->service.validateSettlement(bill));
    }
    @Test void businessDisplayOnlyCostDoesNotChangeTheAllocationSource(){
        Map<String,Object> saved=snapshot();
        Map<String,Object> bill=row("companyDeptId",110L,"month","2025-02","currency","CNY","personnelSnapshot",JSON.toJSONString(saved));
        when(work.selectCalendars()).thenReturn(Arrays.asList(row("calendarId",1L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480)));
        when(work.selectBudgetRates(1L,"2025-02-01","2025-02-28")).thenReturn(Arrays.asList(row("costMode","MONTHLY","unitCost",bd("10000.00"),"currency","CNY")));
        Map<String,Object> refreshed=snapshot();
        assertEquals(bd("7000.00"),refreshed.get("publicAmount"));
        assertEquals(bd("10000.00"),((Map<?,?>)((List<?>)refreshed.get("personnelDetails")).get(0)).get("totalAmount"));
        assertDoesNotThrow(()->service.validateSource(bill));
    }
    @Test void creditsConserveEveryDayAndMonthlySourceAmountsIncludingTinyShares(){
        Map<String,Object> source=row("publicAmount",bd("0.03"),"itLoss",row("netLoss",bd("0.03"),"transfers",Arrays.asList(
            row("projectId",9L,"amount",bd("0.01")),row("projectId",16L,"amount",bd("0.02")))));
        Map<String,BigDecimal> dates=new TreeMap<>();dates.put("2025-02-01",bd("0.01"));dates.put("2025-02-02",bd("0.01"));dates.put("2025-02-03",bd("0.01"));
        List<Map<String,Object>> credits=BusinessPublicExpenseDailyService.transferCredits(1L,source,dates,false);
        Map<Long,BigDecimal> byProject=new HashMap<>();Map<String,BigDecimal> byDay=new HashMap<>();
        for(Map<String,Object> credit:credits){assertTrue(((BigDecimal)credit.get("amount")).signum()<0);byProject.merge((Long)credit.get("projectId"),(BigDecimal)credit.get("amount"),BigDecimal::add);byDay.merge((String)credit.get("bizDate"),(BigDecimal)credit.get("amount"),BigDecimal::add);}
        assertEquals(bd("-0.01"),byProject.get(9L));assertEquals(bd("-0.02"),byProject.get(16L));
        for(String day:dates.keySet())assertEquals(bd("0.00"),byDay.get(day).add(dates.get(day)));
    }
    @Test void partialOwnerSubmissionTransfersOnlyItsShareAndKeepsPayrollSeparate(){
        Map<String,Object> source=row("publicAmount",bd("9000.00"),"itLoss",row("netLoss",bd("7000.00")));
        Map<Long,BigDecimal> recipient=new TreeMap<>();recipient.put(12L,bd("4500.00"));
        assertEquals(bd("3500.00"),BusinessPublicExpenseDailyService.itRecipients(source,recipient).get(12L));
        recipient.put(14L,bd("4500.00"));
        assertEquals(bd("7000.00"),BusinessPublicExpenseDailyService.itRecipients(source,recipient).values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void dailyTransferIsBalancedIdempotentAndRecalledWithItsRecipients(){
        exerciseDailyTransfer(false);
    }
    @Test void combinedDailyTransferUsesTheFullBillAsBasisAndStillConservesITCredits(){
        exerciseDailyTransfer(true);
    }
    @Test void combinedDailyTransferDeductsRetainedHistoryAndTransfersAllItExactlyOnce(){
        exerciseDailyTransfer(true,bd("230.33"));
    }
    @Test void partialCombinedSubmissionTransfersOnlyTheITFractionOfItsFullAmount(){
        Map<String,Object> source=row("publicAmount",bd("300.00"),"itLoss",row("netLoss",bd("200.00")));
        Map<Long,BigDecimal> recipient=new TreeMap<>();recipient.put(12L,bd("300.00"));
        assertEquals(bd("100.00"),BusinessPublicExpenseDailyService.itRecipients(source,recipient,bd("600.00")).get(12L));
        recipient.put(14L,bd("300.00"));
        assertEquals(bd("200.00"),BusinessPublicExpenseDailyService.itRecipients(source,recipient,bd("600.00")).values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        recipient.put(15L,bd("0.01"));
        assertThrows(ServiceException.class,()->BusinessPublicExpenseDailyService.itRecipients(source,recipient,bd("600.00")));
    }
    private void exerciseDailyTransfer(boolean combined){
        exerciseDailyTransfer(combined,bd("0.00"));
    }
    private void exerciseDailyTransfer(boolean combined,BigDecimal retained){
        BusinessPublicExpenseDailyService daily=new BusinessPublicExpenseDailyService();
        ReflectionTestUtils.setField(daily,"mapper",mapper);ReflectionTestUtils.setField(daily,"projects",projects);
        IBusinessAccountingService accounting=mock(IBusinessAccountingService.class);ReflectionTestUtils.setField(daily,"accounting",accounting);
        Map<String,Object> source=row("publicAmount",bd("300.00"),"sourceMode","DEPARTMENT_NET_V1","itLoss",row("netLoss",bd("200.00"),"transfers",Arrays.asList(row("projectId",9L,"amount",bd("200.00")))));
        Map<String,Object> bill=row("billId",1L,"companyDeptId",110L,"month","2025-02","recognitionMode","DAILY_V1","status","PUBLISHED","personnelSnapshot",JSON.toJSONString(source));
        if(combined){bill.put("totalAmount",bd("600.00"));bill.put("personnelAmount",bd("300.00"));}
        when(mapper.selectBill(1L)).thenReturn(bill);when(mapper.selectBillForUpdate(1L)).thenReturn(bill);
        when(mapper.selectOwnerAllocations(1L)).thenReturn(Arrays.asList(row("allocationId",2L,"costPool",combined?"COMBINED":"PERSONNEL","status","SUBMITTED")));
        when(mapper.selectProjectAllocations(2L)).thenReturn(Arrays.asList(row("projectId",12L,"amount",bd(combined?"600.00":"300.00").subtract(retained))));
        for(Long id:Arrays.asList(9L,12L)){BusinessProject p=new BusinessProject();p.setProjectId(id);p.setAccountingState("OPEN");p.setDelFlag("0");p.setPlanStartDate(java.sql.Date.valueOf("2025-02-02"));p.setPlanEndDate(java.sql.Date.valueOf("2025-02-03"));when(projects.selectProjectByIdForUpdate(id)).thenReturn(p);}
        Map<String,Map<String,Object>> stored=new TreeMap<>();
        if(retained.signum()>0){
            Map<String,Object> history=row("billId",1L,"projectId",11L,"bizDate",java.sql.Date.valueOf("2025-02-01"),"amount",retained,"itTransferAmount",bd("0.00"),"confirmed",false);
            stored.put("11:"+history.get("bizDate"),history);
            when(mapper.selectRetainedDailyCosts(1L)).thenReturn(Arrays.asList(row("projectId",11L,"amount",retained)));
        }
        when(mapper.selectDailyRows(1L)).thenAnswer(call->new ArrayList<>(stored.values()));
        doAnswer(call->{Map<String,Object> r=new LinkedHashMap<>((Map<String,Object>)call.getArgument(0));stored.put(r.get("projectId")+":"+r.get("bizDate"),r);return 1;}).when(mapper).upsertDailyRow(anyMap());
        doAnswer(call->{Map<String,Object> r=call.getArgument(0);stored.remove(r.get("projectId")+":"+r.get("bizDate"));return 1;}).when(mapper).deleteDailyRow(anyMap());
        daily.synchronize(1L);assertEquals(retained.signum()>0?5:4,stored.size());
        assertEquals(bd(combined?"400.00":"100.00"),stored.values().stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(bd("-200.00"),stored.values().stream().map(r->(BigDecimal)r.getOrDefault("itTransferAmount",BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add));
        clearInvocations(mapper,accounting);daily.synchronize(1L);verify(mapper,never()).upsertDailyRow(anyMap());verifyNoInteractions(accounting);
        bill.put("status","SETTLED");daily.synchronize(1L);
        projects.selectProjectByIdForUpdate(9L).setAccountingState("CLOSED");clearInvocations(mapper,accounting);
        daily.synchronize(1L);verify(mapper,never()).upsertDailyRow(anyMap());verifyNoInteractions(accounting);
        projects.selectProjectByIdForUpdate(9L).setAccountingState("OPEN");bill.put("status","DRAFT");
        daily.synchronize(1L);verify(mapper,times(4)).deleteDailyRow(anyMap());verify(accounting,times(4)).recalculatePublicExpenseCost(anyLong(),any(),anyString());
        if(retained.signum()>0){assertEquals(1,stored.size());assertEquals(retained,stored.values().iterator().next().get("amount"));}
    }
    @Test void missingAutomaticPayrollCannotHideBehindAnExistingPartialResult(){
        BusinessMemberDayCostService memberDays=mock(BusinessMemberDayCostService.class);ReflectionTestUtils.setField(service,"memberDays",memberDays);
        Map<String,Object> item=it(9L,"-100.00");item.put("costPolicyVersion","MEMBER_DAYS_V1");item.put("personnelCost",bd("100.00"));
        when(mapper.selectItLossProjects(110L,"2025-02","CNY")).thenReturn(Arrays.asList(item));
        BusinessProject project=new BusinessProject();when(projects.selectProjectById(9L)).thenReturn(project);
        when(memberDays.calculate(eq(project),any(),any())).thenReturn(Arrays.asList(row("pricingStatus","PRICED","amount",bd("200.00"))));
        assertThrows(ServiceException.class,this::snapshot);
    }
    static Map<String,Object> it(Long id,String profit){return row("projectId",id,"projectName","IT项目"+id,"profitAmount",bd(profit),"resultCount",20,"accountingState","OPEN","delFlag","0","latestDate","2025-02-28");}
    static BigDecimal bd(String s){return new BigDecimal(s);}
    static Map<String,Object> row(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
}
