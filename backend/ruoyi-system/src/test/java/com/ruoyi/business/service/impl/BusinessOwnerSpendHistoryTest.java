package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessOwnerSpendHistoryTest
{
    @Mock BusinessProjectMapper mapper;
    @Mock BusinessAccountingMapper accountingMapper;
    @Mock BusinessPublicExpenseMapper publicExpenses;
    @Mock BusinessMemberDayCostService memberDays;
    @Mock BusinessProjectWorkMapper workMapper;
    @InjectMocks BusinessProjectServiceImpl service;
    BusinessProject project;
    LocalDate date=LocalDate.of(2024,2,29);
    java.sql.Date sqlDate=java.sql.Date.valueOf(date);

    @BeforeEach void setup()
    {
        project=new BusinessProject();project.setProjectId(13L);project.setProjectName("测试项目");
        project.setMainOwnerUserId(23L);project.setBaseCurrency("CNY");project.setStatus("ACTIVE");
        project.setCostPolicyVersion("MEMBER_DAYS_V1");project.setDeliveryPolicyVersion("SEPARATED_V1");project.setAccountingState("OPEN");
        when(mapper.selectProjectById(13L)).thenReturn(project);
    }

    @Test void dailyBreakdownMatchesCardAndDoesNotDoubleCountInternalOrPublicCosts()
    {
        when(accountingMapper.sumProjectFacts(13L,sqlDate)).thenReturn(map("costAmount","80","internalProjectCost","30","bonusCost","10","publicCost","100"));
        when(publicExpenses.sumDailyCost(13L,sqlDate)).thenReturn(map("monthlyFactAmount","100","amount","5","estimatedAmount","2"));
        Map<String,Object> priced=cost("甲","PRICED","20",date.toString());
        Map<String,Object> pending=cost("乙","PENDING",null,date.toString());pending.put("issue","缺少有效用人成本");
        when(memberDays.calculate(project,date,date)).thenReturn(Arrays.asList(priced,pending));
        Map<String,Object> original=fact("REVERSED","40"),reversal=fact("CONFIRMED","-40"),current=fact("CONFIRMED","80");
        Map<String,Object> draft=fact("DRAFT","999");
        Map<String,Object> revenue=fact("CONFIRMED","999");revenue.put("factKind","REVENUE");
        Map<String,Object> monthlyPublic=fact("CONFIRMED","100");monthlyPublic.put("categoryCode","COMPANY_PUBLIC_COST");
        when(accountingMapper.selectFacts(anyMap())).thenReturn(Arrays.asList(original,reversal,current,draft,revenue,monthlyPublic));
        Map<String,Object> result=service.ownerSpendHistory(13L,null,date.toString(),23L,false);
        Map<?,?> row=rows(result).get(0),totals=(Map<?,?>)result.get("totals");
        assertEquals(new BigDecimal("115.00"),row.get("amount"));
        assertEquals(new BigDecimal("50.00"),row.get("projectCost"));
        assertEquals(new BigDecimal("30.00"),row.get("internalProjectCost"));
        assertEquals(new BigDecimal("5.00"),row.get("publicCost"));
        assertEquals(new BigDecimal("2.00"),row.get("publicEstimatedCost"));
        assertEquals(1,row.get("pendingPersonnelCount"));assertEquals(row.get("amount"),totals.get("amount"));
        List<?> expenseItems=(List<?>)row.get("expenseItems");assertEquals(3,expenseItems.size());
        assertEquals("缺少有效用人成本",((Map<?,?>)((List<?>)row.get("personnelItems")).get(1)).get("calculationDetail"));
        verify(accountingMapper).selectFacts(argThat(query->query.get("projectId").equals(13L)&&Boolean.TRUE.equals(query.get("viewAll"))&&date.toString().equals(query.get("dateFrom"))&&date.toString().equals(query.get("dateTo"))));
        verify(memberDays,never()).synchronize(anyLong());
        verify(accountingMapper,never()).insertFact(any());
    }

    @Test void leapMonthReturnsEachDayAndCalculatesPersonnelOnlyOnce()
    {
        when(memberDays.calculate(project,LocalDate.of(2024,2,1),date)).thenReturn(Collections.singletonList(cost("甲","PRICED","20",date.toString())));
        Map<String,Object> result=service.ownerSpendHistory(13L,"2024-02",null,23L,false);
        assertEquals(29,rows(result).size());assertEquals("2024-02-29",rows(result).get(0).get("bizDate"));
        assertEquals("2024-02-01",rows(result).get(28).get("bizDate"));
        assertEquals(new BigDecimal("20.00"),((Map<?,?>)result.get("totals")).get("amount"));
        verify(memberDays,times(1)).calculate(eq(project),any(),any());
    }

    @Test void defaultMonthStopsAtTodayAndDoesNotProjectFutureCosts()
    {
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        Map<String,Object> result=service.ownerSpendHistory(13L,null,null,23L,false);
        assertEquals(today.toString(),result.get("dateTo"));assertEquals(today.getDayOfMonth(),rows(result).size());
        verify(memberDays).calculate(project,today.withDayOfMonth(1),today);
    }

    @ParameterizedTest @ValueSource(strings={"2024-13","2024-2","2024-02-30","bad","9999-01","1899-01"})
    void invalidMonthIsRejectedBeforeAnyCostReads(String month)
    {
        assertThrows(ServiceException.class,()->service.ownerSpendHistory(13L,month,null,23L,false));
        verifyNoInteractions(accountingMapper,memberDays,publicExpenses,workMapper);
    }

    @ParameterizedTest @ValueSource(strings={"2024-02-30","2024-2-29","bad","9999-01-01","1899-01-01"})
    void invalidDayIsRejected(String day)
    {assertThrows(ServiceException.class,()->service.ownerSpendHistory(13L,null,day,23L,false));}

    @Test void conflictingFiltersAndOtherOwnersCannotReadHistory()
    {
        assertThrows(ServiceException.class,()->service.ownerSpendHistory(13L,"2024-02",date.toString(),23L,false));
        assertThrows(ServiceException.class,()->service.ownerSpendHistory(13L,null,date.toString(),24L,false));
        assertThrows(ServiceException.class,()->service.ownerSpendHistory(13L,null,date.toString(),null,false));
        verifyNoInteractions(accountingMapper,memberDays,publicExpenses,workMapper);
    }

    @Test void administratorCanReadAndVndAmountIsNotConverted()
    {
        project.setBaseCurrency("VND");
        when(memberDays.calculate(project,date,date)).thenReturn(Collections.singletonList(cost("甲","PRICED","200000",date.toString())));
        Map<String,Object> result=service.ownerSpendHistory(13L,null,date.toString(),1L,true);
        assertEquals("VND",result.get("currency"));assertEquals(new BigDecimal("200000.00"),rows(result).get(0).get("amount"));
    }

    @Test void accountingClosedUsesStoredPersonnelAndNeverReprices()
    {
        project.setAccountingState("CLOSED");
        when(memberDays.dayCosts(13L,sqlDate)).thenReturn(Collections.singletonList(cost("甲","PRICED","20",date.toString())));
        assertEquals(new BigDecimal("20.00"),rows(service.ownerSpendHistory(13L,null,date.toString(),23L,false)).get(0).get("personnelCost"));
        verify(memberDays,never()).calculate(any(),any(),any());
    }

    @Test void legacyPersonnelDetailsAndActualWorkPersonnelAreSupported()
    {
        project.setCostPolicyVersion("LEGACY_V1");
        when(accountingMapper.sumProjectPersonnelCost(13L,sqlDate)).thenReturn(new BigDecimal("15"));
        when(accountingMapper.selectProjectPersonnelCostDetails(13L,sqlDate)).thenReturn(Collections.singletonList(cost("甲",null,"15",date.toString())));
        Map<?,?> legacy=rows(service.ownerSpendHistory(13L,null,date.toString(),23L,false)).get(0);
        assertEquals(new BigDecimal("15.00"),legacy.get("personnelCost"));assertEquals(1,((List<?>)legacy.get("personnelItems")).size());
        project.setCostPolicyVersion("ACTUAL_WORK_V1");
        when(workMapper.selectWorkCosts(13L,sqlDate)).thenReturn(Collections.singletonList(cost("甲","PRICED","30",date.toString())));
        assertEquals(new BigDecimal("30.00"),rows(service.ownerSpendHistory(13L,null,date.toString(),23L,false)).get(0).get("personnelCost"));
        verifyNoInteractions(memberDays);
    }

    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> result){return (List<Map<String,Object>>)result.get("rows");}
    Map<String,Object> map(String... values){Map<String,Object> result=new HashMap<>();for(int i=0;i<values.length;i+=2)result.put(values[i],new BigDecimal(values[i+1]));return result;}
    Map<String,Object> cost(String name,String status,String amount,String date){Map<String,Object> item=new HashMap<>();item.put("userName",name);item.put("pricingStatus",status);item.put("amount",amount==null?null:new BigDecimal(amount));item.put("bizDate",date);return item;}
    Map<String,Object> fact(String status,String amount){Map<String,Object> item=new HashMap<>();item.put("factId",amount);item.put("bizDate",sqlDate);item.put("factKind","COST");item.put("categoryCode","OTHER_EXPENSE");item.put("categoryName","其他支出");item.put("status",status);item.put("amount",new BigDecimal(amount));return item;}
}
