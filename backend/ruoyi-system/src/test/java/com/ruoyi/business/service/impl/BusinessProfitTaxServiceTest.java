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
    @Test void finalBalanceAndLosses() {
        assertEquals(new BigDecimal("1000.00"),BusinessProfitTaxService.tax(new BigDecimal("10000"),new BigDecimal("10")));
        assertEquals(new BigDecimal("0.00"),BusinessProfitTaxService.tax(new BigDecimal("-10000"),new BigDecimal("10")));
        assertEquals(new BigDecimal("10000.00"),BusinessProfitTaxService.tax(new BigDecimal("10000"),new BigDecimal("100")));
        assertEquals(new BigDecimal("0.00"),BusinessProfitTaxService.tax(BigDecimal.ZERO,new BigDecimal("25")));
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
        List<Map<String,Object>> rows=Arrays.asList(row("projectId",1,"profitAmount",100,"taxRate",10),row("projectId",1,"profitAmount",-80,"taxRate",10),row("projectId",2,"profitAmount",-100,"taxRate",10));
        BusinessProfitTaxService.calculateSeries(rows);
        assertEquals(new BigDecimal("10.00"),rows.get(0).get("taxAmount"));
        assertEquals(new BigDecimal("-8.00"),rows.get(1).get("taxAmount"));
        assertEquals(new BigDecimal("-72.00"),rows.get(1).get("afterTaxProfit"));
        assertEquals(new BigDecimal("0.00"),rows.get(2).get("taxAmount"));
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
