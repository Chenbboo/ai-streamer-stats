package com.ruoyi.business.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.mapper.BusinessProfitTaxMapper;
import com.ruoyi.common.exception.ServiceException;
class BusinessProfitTaxServiceTest {
    static Map<String,Object> row(Object... pairs){Map<String,Object> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    @Test void bonusRequiresAvailablePositiveAfterTaxProfitAtCurrencyPrecision() {
        for(String value:Arrays.asList("0","-100.00","0.0049"))
            assertThrows(ServiceException.class,()->BusinessProfitTaxService.requirePositiveBonusProfit(row("available",true,"afterTaxProfit",new BigDecimal(value))));
        assertThrows(ServiceException.class,()->BusinessProfitTaxService.requirePositiveBonusProfit(row("available",false,"afterTaxProfit",10000)));
        assertThrows(ServiceException.class,()->BusinessProfitTaxService.requirePositiveBonusProfit(null));
        assertEquals(new BigDecimal("0.01"),BusinessProfitTaxService.requirePositiveBonusProfit(row("available",true,"afterTaxProfit","0.01")));
    }
    @Test void finalBalanceAndLosses() {
        assertEquals(new BigDecimal("1000.00"),BusinessProfitTaxService.tax(new BigDecimal("10000"),new BigDecimal("10")));
        assertEquals(new BigDecimal("0.00"),BusinessProfitTaxService.tax(new BigDecimal("-10000"),new BigDecimal("10")));
        assertEquals(new BigDecimal("10000.00"),BusinessProfitTaxService.tax(new BigDecimal("10000"),new BigDecimal("100")));
        assertEquals(new BigDecimal("0.00"),BusinessProfitTaxService.tax(BigDecimal.ZERO,new BigDecimal("25")));
    }
    @Test void monthlyBonusUsesShanghaiPreviousCalendarMonthAndRejectsUnfinishedMonths() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Collections.emptyList());
        String[][] dates={{"2026-09-30T16:00:00Z","2026-09","2026-09-30"},{"2026-01-01T00:00:00Z","2025-12","2025-12-31"},{"2024-03-01T00:00:00Z","2024-02","2024-02-29"}};
        for(String[] date:dates){service.setBonusClock(java.time.Clock.fixed(java.time.Instant.parse(date[0]),java.time.ZoneOffset.UTC));
            Map<String,Object> result=service.previousMonthResult(1L);assertEquals(date[1],result.get("month"));assertEquals(date[2],result.get("dateTo"));}
        assertThrows(ServiceException.class,()->service.monthlyBonusResult(1L,"2024-03"));
        assertThrows(ServiceException.class,()->service.monthlyBonusResult(1L,"2024-13"));
        assertThrows(ServiceException.class,()->service.monthlyBonusResult(1L,"2024-2"));
    }
    @Test void monthlySnapshotCannotBeUsedAfterItsMonthProfitChanges() {
        Map<String,Object> result=row("available",true,"afterTaxProfit","100.00");
        assertDoesNotThrow(()->BusinessProfitTaxService.requireMonthlyBonusProfit(result,new BigDecimal("100.00")));
        assertEquals("monthlyProfitChanged",BusinessProfitTaxService.monthlyBonusBlockReason(result,new BigDecimal("200.00")));
        assertThrows(ServiceException.class,()->BusinessProfitTaxService.requireMonthlyBonusProfit(result,new BigDecimal("200.00")));
    }
    @Test void roundedTaxNeverExceedsPositiveBalance() {
        for(String value:Arrays.asList("0.0049","0.005","0.015","1.005")){
            BigDecimal profit=new BigDecimal(value),tax=BusinessProfitTaxService.tax(profit,new BigDecimal("100"));
            assertTrue(tax.compareTo(profit)<=0,"Tax exceeds remaining profit "+value);
            assertEquals(2,tax.scale());
        }
    }
    @Test void historicalSnapshotDoesNotPromptCompanySettingsAgain() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Arrays.asList(row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-08-31","profitAmount",100,"taxRate",0,"taxConfigured","0","taxFrozen","1")));
        Map<String,Object> result=row("summary",row("profitAmount",100));service.decorate(result,row());
        assertEquals(0,result.get("taxUnconfiguredCount"));
    }
    @Test void cumulativeCalculationReleasesTaxWhenLaterCostsReduceProfit() {
        List<Map<String,Object>> rows=Arrays.asList(row("projectId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-01","profitAmount",100,"taxRate",10),row("projectId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-02","profitAmount",-80,"taxRate",10),row("projectId",2,"companyDeptId",111,"currency","CNY","bizDate","2026-09-02","profitAmount",-100,"taxRate",10));
        BusinessProfitTaxService.calculateSeries(rows);
        assertEquals(new BigDecimal("10.00"),rows.get(0).get("taxAmount"));
        assertEquals(new BigDecimal("-8.00"),rows.get(1).get("taxAmount"));
        assertEquals(new BigDecimal("-72.00"),rows.get(1).get("afterTaxProfit"));
        assertEquals(new BigDecimal("0.00"),rows.get(2).get("taxAmount"));
    }
    @Test void openProjectsInOneCompanyOffsetBeforeTax() {
        List<Map<String,Object>> rows=Arrays.asList(
            row("projectId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",7440,"taxRate",10),
            row("projectId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",new BigDecimal("-4390.97"),"taxRate",10));
        BusinessProfitTaxService.calculateSeries(rows);
        BigDecimal totalTax=rows.stream().map(value->(BigDecimal)value.get("taxAmount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(new BigDecimal("304.90"),totalTax);
        assertEquals(new BigDecimal("2744.13"),rows.stream().map(value->(BigDecimal)value.get("afterTaxProfit")).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void projectFilterUsesCompanyOffsetAndNeverReturnsOtherProjectAdjustments() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenAnswer(call->Arrays.asList(
            row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",1000,"taxRate",10,"taxConfigured","1"),
            row("projectId",2,"resultId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",-800,"taxRate",10,"taxConfigured","1"),
            row("projectId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",-100,"taxRate",10,"taxConfigured","1","isAdjustment",1)));
        Map<String,Object> daily=row("resultId",1,"profitAmount",1000),summary=row("profitAmount",1000),result=row("results",Arrays.asList(daily),"summary",summary);
        service.decorate(result,row("projectId",1));
        assertEquals(new BigDecimal("10.00"),summary.get("taxAmount"));assertEquals(summary.get("taxAmount"),daily.get("taxAmount"));
        assertEquals(new BigDecimal("990.00"),summary.get("afterTaxProfit"));assertTrue(((List<?>)result.get("departmentAdjustments")).isEmpty());
        Map<String,Object> allDaily=row("resultId",1,"profitAmount",1000);service.decorate(row("results",Arrays.asList(allDaily)),row());
        assertEquals(allDaily.get("taxAmount"),daily.get("taxAmount"));
    }
    @Test void projectResultUsesTheSameCompanyOffsetTaxCalculation() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Arrays.asList(
            row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",1000,"taxRate",10,"taxConfigured","1"),
            row("projectId",2,"resultId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",-800,"taxRate",10,"taxConfigured","1")));
        Map<String,Object> result=service.projectResult(1L);
        assertEquals(new BigDecimal("20.00"),result.get("taxAmount"));assertEquals(new BigDecimal("980.00"),result.get("afterTaxProfit"));
        assertEquals(true,result.get("available"));assertEquals(1,result.get("resultCount"));
    }
    @Test void previousMonthTotalsExcludeEarlierAndCurrentMonthButCarryCompanyTaxBalance() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Arrays.asList(
            row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-08-31","profitAmount",100,"taxRate",10,"taxConfigured","1"),
            row("projectId",2,"resultId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-08-31","profitAmount",-200,"taxRate",10,"taxConfigured","1"),
            row("projectId",1,"resultId",3,"companyDeptId",110,"currency","CNY","bizDate","2026-09-01","profitAmount",1000,"taxRate",10,"taxConfigured","1"),
            row("projectId",1,"resultId",4,"companyDeptId",110,"currency","CNY","bizDate","2026-09-30","profitAmount",200,"taxRate",10,"taxConfigured","1"),
            row("projectId",1,"resultId",5,"companyDeptId",110,"currency","CNY","bizDate","2026-10-01","profitAmount",5000,"taxRate",10,"taxConfigured","1")));
        Map<String,Object> result=service.projectPeriodResult(1L,java.time.LocalDate.parse("2026-09-01"),java.time.LocalDate.parse("2026-09-30"));
        assertEquals(new BigDecimal("1200"),result.get("pretaxProfit"));
        assertEquals(new BigDecimal("110.00"),result.get("taxAmount"));
        assertEquals(new BigDecimal("1090.00"),result.get("afterTaxProfit"));
        assertEquals(2,result.get("resultCount"));assertEquals("2026-09",result.get("month"));assertEquals("CNY",result.get("currency"));
        org.mockito.ArgumentCaptor<Map<String,Object>> query=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectSeries(query.capture());assertEquals(1L,query.getValue().get("projectId"));
        assertEquals("2026-09-30",query.getValue().get("dateTo"));assertFalse(query.getValue().containsKey("dateFrom"));
    }
    @Test void monthlyResultIncludesTaxRefundAndApprovedAdjustmentsOnlyInPostingMonth() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Arrays.asList(
            row("projectId",1,"resultId",1,"companyDeptId",110,"currency","VND","bizDate","2026-08-31","profitAmount",10000,"taxRate",10,"taxConfigured","1"),
            row("projectId",1,"companyDeptId",110,"currency","VND","bizDate","2026-09-30","profitAmount",-1000,"taxRate",10,"taxConfigured","1","isAdjustment",1)));
        Map<String,Object> result=service.projectPeriodResult(1L,java.time.LocalDate.parse("2026-09-01"),java.time.LocalDate.parse("2026-09-30"));
        assertEquals(new BigDecimal("-1000"),result.get("pretaxProfit"));assertEquals(new BigDecimal("-100.00"),result.get("taxAmount"));
        assertEquals(new BigDecimal("-900.00"),result.get("afterTaxProfit"));assertEquals(true,result.get("available"));
        assertEquals(true,result.get("hasClosedAdjustments"));assertEquals("VND",result.get("currency"));
    }
    @Test void monthlyResultDistinguishesNoResultsFromAnActualZeroAndFlagsMissingTaxRate() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Collections.singletonList(
            row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-08-31","profitAmount",500,"taxRate",0,"taxConfigured","0")));
        java.time.LocalDate from=java.time.LocalDate.parse("2026-09-01"),to=java.time.LocalDate.parse("2026-09-30");
        assertEquals(false,service.projectPeriodResult(1L,from,to).get("available"));
        when(mapper.selectSeries(anyMap())).thenReturn(Collections.singletonList(
            row("projectId",1,"resultId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",0,"taxRate",0,"taxConfigured","0")));
        Map<String,Object> result=service.projectPeriodResult(1L,from,to);
        assertEquals(true,result.get("available"));assertEquals(BigDecimal.ZERO,result.get("afterTaxProfit"));
        assertEquals(false,result.get("taxConfigured"));
    }
    @Test void differentCurrenciesAndRatesStaySeparate() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Arrays.asList(row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-15","profitAmount",1000,"taxRate",10,"taxConfigured","1"),row("projectId",2,"resultId",2,"companyDeptId",111,"currency","USD","bizDate","2026-09-15","profitAmount",1000,"taxRate",20,"taxConfigured","1")));
        Map<String,Object> cny=row("currency","CNY","profitAmount",1000),usd=row("currency","USD","profitAmount",1000),summary=row("profitAmount",null);
        service.decorate(row("summary",summary,"summaryByCurrency",Arrays.asList(cny,usd)),row());
        assertNull(summary.get("taxAmount"));assertNull(summary.get("afterTaxProfit"));assertEquals(new BigDecimal("900.00"),cny.get("afterTaxProfit"));assertEquals(new BigDecimal("800.00"),usd.get("afterTaxProfit"));
    }
    @Test void timeRangeCarriesPriorBalanceAndSummariesMatchDailyValues() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        List<Map<String,Object>> rows=Arrays.asList(row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-08-31","profitAmount",100,"taxRate",10,"taxConfigured","1"),row("projectId",1,"resultId",2,"companyDeptId",110,"currency","CNY","bizDate","2026-09-01","profitAmount",-80,"taxRate",10,"taxConfigured","1"));
        when(mapper.selectSeries(anyMap())).thenReturn(rows);
        Map<String,Object> summary=row("profitAmount",-80),daily=row("resultId",2,"profitAmount",-80),result=row("summary",summary,"results",Arrays.asList(daily));
        service.decorate(result,row("dateFrom","2026-09-01"));
        assertEquals(new BigDecimal("-8.00"),summary.get("taxAmount"));assertEquals(new BigDecimal("-72.00"),daily.get("afterTaxProfit"));
    }
    @Test void onlyAuthorizedBossCanSetValidRateAndStaleVersionsAreRejected() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectCompanies(1L)).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class,()->service.save(110L,row("taxRate",10),1L,"actor"));
        when(mapper.selectCompanies(1L)).thenReturn(Arrays.asList(row("companyDeptId",110L)));when(mapper.lockCompany(110L)).thenReturn(110L);
        when(mapper.selectPolicy(110L)).thenReturn(null);
        for(Object invalid:Arrays.asList(-1,101,"NaN","1.12345","", "null"))assertThrows(ServiceException.class,()->service.save(110L,row("taxRate",invalid,"version",0,"reason","test"),1L,"actor"));
        assertThrows(ServiceException.class,()->service.save(110L,row("taxRate",10,"version",1,"reason","test"),1L,"actor"));
        service.save(110L,row("taxRate",10,"version",0,"reason","test"),1L,"actor");verify(mapper,times(1)).savePolicy(anyMap());verify(mapper,times(1)).insertEvent(anyMap());
    }
    @Test void approvedClosedAdjustmentsAreTaxedAndIncludedOnce() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        when(mapper.selectSeries(anyMap())).thenReturn(Arrays.asList(row("projectId",1,"resultId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-08-31","profitAmount",10000,"taxRate",10,"taxConfigured","1"),row("projectId",1,"companyDeptId",110,"currency","CNY","bizDate","2026-09-01","profitAmount",-1000,"taxRate",10,"taxConfigured","1","isAdjustment",1)));
        Map<String,Object> summary=row("profitAmount",0),result=row("summary",summary,"summaryByCurrency",Collections.emptyList());
        service.decorate(result,row("dateFrom","2026-09-01"));
        assertEquals(new BigDecimal("-1000"),summary.get("pretaxProfit"));assertEquals(new BigDecimal("-100.00"),summary.get("taxAmount"));assertEquals(new BigDecimal("-900.00"),summary.get("afterTaxProfit"));
        assertEquals(1,((List<?>)result.get("summaryByCurrency")).size());
        assertEquals(true,result.get("hasClosedAdjustments"));
    }
    @Test void closingSavesTaxAfterAllRecordedCosts() {
        BusinessProfitTaxMapper mapper=mock(BusinessProfitTaxMapper.class);BusinessProfitTaxService service=service(mapper);
        Map<String,Object> basis=row("projectId",1,"taxRate",10,"pretaxProfit",10000);
        when(mapper.selectProjectBasis(1L)).thenReturn(basis);service.freeze(1L,"boss");
        assertEquals(new BigDecimal("1000.00"),basis.get("taxAmount"));assertEquals(new BigDecimal("9000.00"),basis.get("afterTaxProfit"));verify(mapper).insertSnapshot(basis);
    }
    private BusinessProfitTaxService service(BusinessProfitTaxMapper mapper){BusinessProfitTaxService s=new BusinessProfitTaxService();ReflectionTestUtils.setField(s,"mapper",mapper);return s;}
}
