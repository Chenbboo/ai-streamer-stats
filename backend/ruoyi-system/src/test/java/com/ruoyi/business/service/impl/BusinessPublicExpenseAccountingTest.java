package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessPublicExpenseMapper;
import com.ruoyi.common.exception.ServiceException;

class BusinessPublicExpenseAccountingTest {
    BusinessAccountingMapper mapper=mock(BusinessAccountingMapper.class);
    BusinessPublicExpenseMapper publicExpenses=mock(BusinessPublicExpenseMapper.class);
    BusinessAccountingServiceImpl service=new BusinessAccountingServiceImpl();
    Date date=java.sql.Date.valueOf("2026-08-31");

    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"mapper",mapper);
        ReflectionTestUtils.setField(service,"publicExpenses",publicExpenses);
        Map<String,Object> project=row("projectId",1L,"companyDeptId",110L,"initiatorUserId",8L,
            "mainOwnerUserId",9L,"status","ACTIVE","accountingState","OPEN","accountingMode","PROFIT");
        when(mapper.selectProjectForAccounting(1L)).thenReturn(project);
        when(mapper.selectProjectForAccountingForUpdate(1L)).thenReturn(project);
        when(mapper.selectNextResultVersion(any(),any())).thenReturn(1,2);
        doAnswer(call->{call.<Map<String,Object>>getArgument(0).put("resultId",100L);return 1;}).when(mapper).insertDailyResult(any());
    }

    @Test void monthlyCostIsDeductedOnceAndRecalculationDoesNotAccumulateIt(){
        when(mapper.sumProjectFacts(1L,date)).thenReturn(row("revenueAmount",money("20000"),"costAmount",money("200"),
            "bonusCost",money("50"),"publicCost",money("10080")));
        when(mapper.sumProjectPersonnelCost(1L,date)).thenReturn(money("300"));
        Map<String,Object> first=service.recalculate(1L,date,8L,"boss",false);
        Map<String,Object> second=service.recalculate(1L,date,8L,"boss",false);
        assertEquals(money("9370"),first.get("profitAmount"));
        assertEquals(first.get("profitAmount"),second.get("profitAmount"));
        assertEquals(money("200"),second.get("costAmount"));
        assertEquals(money("10080"),second.get("publicCost"));
        verify(mapper,never()).insertFact(any());
    }

    @Test void negativePublicExpenseAdjustmentRestoresProfit(){
        when(mapper.sumProjectFacts(1L,date)).thenReturn(row("revenueAmount",money("1000"),"publicCost",money("-100")));
        assertEquals(money("1100"),service.recalculate(1L,date,8L,"boss",false).get("profitAmount"));
    }

    @Test void rejectsManualPublicExpenseEntryBeforeWriting(){
        when(mapper.selectCategoryById(42L)).thenReturn(row("categoryCode","COMPANY_PUBLIC_COST"));
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(1L);fact.setCategoryId(42L);
        ServiceException ex=assertThrows(ServiceException.class,()->service.saveFact(fact,8L,"boss",false));
        assertTrue(ex.getMessage().contains("公共费用"));verify(mapper,never()).insertFact(any());
    }

    @Test void blocksProjectCloseUntilMonthlyCostsAreResolved(){
        when(publicExpenses.countProjectPending(1L)).thenReturn(1);
        assertTrue(assertThrows(ServiceException.class,()->service.ensureProjectCanClose(1L)).getMessage().contains("月结"));
    }

    @Test void dailyViewUsesTheAlreadyDeductedOperatingResult(){
        Map<String,Object> total=row("currency","CNY","profitAmount",money("9370"),"publicCost",money("10080"));
        when(mapper.selectDailySummaryByCurrency(any())).thenReturn(Collections.singletonList(total));
        when(mapper.selectPublicExpenseReferences(any())).thenReturn(Collections.singletonList(row("currency","CNY",
            "projectId",1L,"profitAmount",money("9370"),"recognizedPublicCost",money("10080"),
            "dailyReference",money("463.45"),"pendingCount",0)));
        Map<String,Object> result=service.bossOverview("2026-08-31",8L,false);
        Map<String,Object> reference=(Map<String,Object>)result.get("publicExpenseReference");
        Map<String,Object> shown=((List<Map<String,Object>>)reference.get("byCurrency")).get(0);
        assertEquals(money("9370"),shown.get("referenceProfit"));
        assertEquals(money("10080"),shown.get("dailyReference"));
        assertEquals(money("9370"),total.get("profitAmount"));
        verify(mapper,never()).insertDailyResult(any());verify(mapper,never()).insertFact(any());
    }

    @Test void estimatedDailyCostsAreIncludedAndMonthlyConfirmationDoesNotDeductAgain(){
        when(mapper.sumProjectFacts(1L,date)).thenReturn(row("revenueAmount",money("1000"),"publicCost",BigDecimal.ZERO));
        when(publicExpenses.sumDailyCost(1L,date)).thenReturn(row("amount",money("160"),"estimatedAmount",money("160"),"monthlyFactAmount",BigDecimal.ZERO));
        Map<String,Object> estimated=service.recalculatePublicExpenseCost(1L,date,"test");
        assertEquals(money("840"),estimated.get("profitAmount"));
        assertEquals(money("160"),estimated.get("publicEstimatedCost"));
        when(mapper.sumProjectFacts(1L,date)).thenReturn(row("revenueAmount",money("1000"),"publicCost",money("4800")));
        when(publicExpenses.sumDailyCost(1L,date)).thenReturn(row("amount",money("160"),"estimatedAmount",BigDecimal.ZERO,"monthlyFactAmount",money("4800")));
        Map<String,Object> settled=service.recalculatePublicExpenseCost(1L,date,"test");
        assertEquals(money("840"),settled.get("profitAmount"));
        assertEquals(BigDecimal.ZERO,settled.get("publicEstimatedCost"));
        assertEquals(money("160"),settled.get("publicCost"));
    }

    @Test void incompleteAllocationDoesNotDisplayZeroAsCompleteProfit(){
        when(mapper.selectPublicExpenseReferences(any())).thenReturn(Collections.singletonList(row("currency","CNY",
            "dailyReference",BigDecimal.ZERO,"pendingCount",1)));
        Map<String,Object> result=service.bossOverview("2026-08-31",8L,false);
        Map<String,Object> reference=(Map<String,Object>)result.get("publicExpenseReference");
        assertEquals(1,reference.get("pendingCount"));
        assertNull(((List<Map<String,Object>>)reference.get("byCurrency")).get(0).get("referenceProfit"));
    }

    private static BigDecimal money(String value){return new BigDecimal(value);}
    private static Map<String,Object> row(Object... values){Map<String,Object> row=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)row.put((String)values[i],values[i+1]);return row;}
}
