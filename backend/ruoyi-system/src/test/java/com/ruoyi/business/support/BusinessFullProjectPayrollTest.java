package com.ruoyi.business.support;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

class BusinessFullProjectPayrollTest {
    static Map<String,Object> row(Object... pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}
    Map<String,Object> calendar=row("calendarId",1L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480,"effectiveFrom","2000-01-01");
    Map<String,Object> rate=row("costMode","MONTHLY","unitCost",new BigDecimal("11250.00"),"currency","CNY","effectiveFrom","2000-01-01");
    Map<String,Object> period(long project,int percent){return row("projectId",project,"allocationValue",percent,"confirmationStatus","CONFIRMED","effectiveFrom","2026-09-23","projectStartDate","2026-09-23","projectCurrency","CNY");}
    Map<String,Object> scope(String joined){return row("accountingState","OPEN","delFlag","0","members",Arrays.asList(row("status","0","memberRole","MEMBER","joinedDate",joined)),"plans",Collections.emptyList(),"roles",Collections.emptyList(),"pauses",Collections.emptyList());}
    BusinessFullProjectPayroll payroll(List<Map<String,Object>> periods,Map<Long,Map<String,Object>> scopes){return new BusinessFullProjectPayroll(periods,Arrays.asList(calendar),scopes);}
    BigDecimal sum(BusinessFullProjectPayroll payroll,long project,List<Map<String,Object>> stored){
        BigDecimal result=BigDecimal.ZERO;
        for(int day:new int[]{23,24,25,28,29,30})result=result.add((BigDecimal)payroll.amount(project,LocalDate.of(2026,9,day),calendar,Arrays.asList(rate),stored,"CNY",null).get("amount"));
        return result;
    }
    @Test void onlyParticipatingProjectBearsFullSalaryDespiteTwentyPercentAndLateStart(){
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(20,20)),Collections.singletonMap(20L,scope("2026-09-23")));
        assertEquals(new BigDecimal("11250.00"),sum(payroll,20,Collections.emptyList()));
        Map<String,Object> cost=payroll.amount(20L,LocalDate.of(2026,9,29),calendar,Arrays.asList(rate),Collections.emptyList(),"CNY",null);
        assertEquals(new BigDecimal("100.00"),cost.get("allocationValue"));assertEquals(new BigDecimal("1875.00"),cost.get("amount"));
    }
    @Test void twoProjectWeightsBecomeFortyAndSixtyAndMoneyIsConserved(){
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();scopes.put(1L,scope("2026-09-23"));scopes.put(2L,scope("2026-09-23"));
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(1,20),period(2,30)),scopes);
        BigDecimal one=sum(payroll,1,Collections.emptyList()),two=sum(payroll,2,Collections.emptyList());
        assertEquals(new BigDecimal("4500.00"),one);assertEquals(new BigDecimal("6750.00"),two);
        assertEquals(new BigDecimal("11250.00"),one.add(two));
    }
    @Test void tinyMonthSalaryIsConservedAcrossEveryDayAndThreeEqualProjects(){
        rate.put("unitCost",new BigDecimal("0.03"));Map<Long,Map<String,Object>> scopes=new TreeMap<>();
        for(long id=1;id<=3;id++)scopes.put(id,scope("2026-09-23"));
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(1,10),period(2,10),period(3,10)),scopes);
        BigDecimal total=BigDecimal.ZERO;
        for(long id=1;id<=3;id++){BigDecimal projectTotal=sum(payroll,id,Collections.emptyList());assertEquals(new BigDecimal("0.01"),projectTotal);total=total.add(projectTotal);for(int day:new int[]{23,24,25,28,29,30})assertTrue(((BigDecimal)payroll.amount(id,LocalDate.of(2026,9,day),calendar,Arrays.asList(rate),Collections.emptyList(),"CNY",null).get("amount")).signum()>=0);}
        assertEquals(new BigDecimal("0.03"),total);
    }
    @Test void monthlyAmountsUseOriginalEqualWeightsRatherThanRoundedDisplayPercentages(){
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();for(long id=1;id<=3;id++)scopes.put(id,scope("2026-09-23"));
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(1,10),period(2,10),period(3,10)),scopes);
        for(long id=1;id<=3;id++)assertEquals(new BigDecimal("3750.00"),sum(payroll,id,Collections.emptyList()));
        for(int day:new int[]{23,24,25,28,29,30}){
            BigDecimal total=BigDecimal.ZERO;Map<String,Object> cost=null;
            for(long id=1;id<=3;id++){cost=payroll.amount(id,LocalDate.of(2026,9,day),calendar,Arrays.asList(rate),Collections.emptyList(),"CNY",null);total=total.add((BigDecimal)cost.get("amount"));}
            assertEquals(total,cost.get("fullDailyCost"));
        }
    }
    @Test void closedForeignCurrencyAndMissingCurrencyCostsCannotReduceTheMonthlyBudget(){
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();scopes.put(1L,scope("2026-09-23"));Map<String,Object> closed=scope("2026-09-23");closed.put("accountingState","CLOSED");scopes.put(2L,closed);
        for(String currency:Arrays.asList("VND",null))for(boolean normalize:Arrays.asList(true,false)){
            BusinessFullProjectPayroll payroll=new BusinessFullProjectPayroll(Arrays.asList(period(1,20),period(2,30)),Arrays.asList(calendar),scopes,normalize);
            List<Map<String,Object>> stored=Arrays.asList(row("projectId",2L,"bizDate","2026-09-23","amount",new BigDecimal("100.00"),"currency",currency));
            ServiceException error=assertThrows(ServiceException.class,()->payroll.allocations(java.time.YearMonth.of(2026,9),calendar,Arrays.asList(rate),stored,"CNY",null));
            assertEquals("成本币种与项目不一致",error.getMessage());
        }
    }
    @Test void recurringRawRatiosRoundOnlyWhenConvertingToMoney(){
        rate.put("unitCost",new BigDecimal("0.06"));Map<Long,Map<String,Object>> scopes=new TreeMap<>();scopes.put(1L,scope("2026-09-23"));scopes.put(2L,scope("2026-09-23"));
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(1,1),period(2,11)),scopes);
        assertEquals(new BigDecimal("0.01"),sum(payroll,1,Collections.emptyList()));assertEquals(new BigDecimal("0.05"),sum(payroll,2,Collections.emptyList()));
    }
    @Test void closedAndRemovedProjectCostsAreDeductedBeforeAssigningTheRemainingSalary(){
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();scopes.put(20L,scope("2026-09-23"));Map<String,Object> closed=scope("2026-09-23");closed.put("accountingState","CLOSED");scopes.put(21L,closed);
        List<Map<String,Object>> stored=Arrays.asList(row("projectId",21L,"bizDate","2026-09-23","amount",new BigDecimal("2250.00"),"currency","CNY"));
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(20,20),period(21,30)),scopes);
        assertEquals(new BigDecimal("9000.00"),sum(payroll,20,stored));
        assertEquals(new BigDecimal("2250.00"),stored.get(0).get("amount"));
    }
    @Test void monthlyDetailsIncludeEarlierProjectSharesAndPreservedClosedCosts(){
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();scopes.put(1L,scope("2026-09-23"));scopes.put(2L,scope("2026-09-23"));
        Map<String,Object> earlier=period(1,20);earlier.put("projectEndDate","2026-09-25");
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(earlier,period(2,30)),scopes);
        List<Map<String,Object>> details=payroll.allocations(java.time.YearMonth.of(2026,9),calendar,Arrays.asList(rate),Collections.emptyList(),"CNY",null);
        assertEquals(2,details.size());assertEquals(new BigDecimal("2250.00"),details.get(0).get("amount"));assertEquals(new BigDecimal("9000.00"),details.get(1).get("amount"));
        scopes.get(1L).put("accountingState","CLOSED");
        payroll=payroll(Arrays.asList(earlier,period(2,30)),scopes);
        details=payroll.allocations(java.time.YearMonth.of(2026,9),calendar,Arrays.asList(rate),Arrays.asList(row("projectId",1L,"bizDate","2026-09-23","amount",new BigDecimal("2250.00"),"currency","CNY")),"CNY",null);
        assertEquals(new BigDecimal("2250.00"),details.get(0).get("amount"));assertEquals(new BigDecimal("9000.00"),details.get(1).get("amount"));
    }
    @Test void supportSalaryKeepsPartialWeightsAndOnlyLeavesTheActualMonthlyRemainderPublic(){
        rate.put("unitCost",new BigDecimal("7500.00"));
        BusinessFullProjectPayroll payroll=new BusinessFullProjectPayroll(Arrays.asList(period(20,20)),Arrays.asList(calendar),Collections.singletonMap(20L,scope("2026-09-23")),false);
        assertEquals(new BigDecimal("1500.00"),sum(payroll,20,Collections.emptyList()));
        assertEquals(new BigDecimal("6000.00"),new BigDecimal("7500.00").subtract(sum(payroll,20,Collections.emptyList())));
    }
    @Test void supportMonthlyTargetsAndDailyCostsMatchExactlyAfterPreservingOldProjectCosts(){
        rate.put("unitCost",new BigDecimal("7500.00"));Map<Long,Map<String,Object>> scopes=new TreeMap<>();scopes.put(19L,scope("2026-09-23"));scopes.put(20L,scope("2026-09-23"));
        BusinessFullProjectPayroll payroll=new BusinessFullProjectPayroll(Arrays.asList(period(19,30),period(20,70)),Arrays.asList(calendar),scopes,false);
        List<Map<String,Object>> stored=Arrays.asList(row("projectId",17L,"bizDate","2026-09-21","amount",new BigDecimal("102.27"),"currency","CNY"));
        assertEquals(new BigDecimal("2219.32"),sum(payroll,19,stored));assertEquals(new BigDecimal("5178.41"),sum(payroll,20,stored));
        assertEquals(new BigDecimal("7500.00"),payroll.allocations(java.time.YearMonth.of(2026,9),calendar,Arrays.asList(rate),stored,"CNY",null).stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void supportWithoutProjectsLeavesItsEntireSalaryAvailableForPublicCosts(){
        BusinessFullProjectPayroll payroll=new BusinessFullProjectPayroll(Collections.emptyList(),Arrays.asList(calendar),Collections.emptyMap(),false);
        assertTrue(payroll.allocations(java.time.YearMonth.of(2026,9),calendar,Arrays.asList(rate),Collections.emptyList(),"CNY",null).isEmpty());
    }
    @Test void missingZeroAndUnconfirmedWeightsStayUnknown(){
        assertThrows(ServiceException.class,()->BusinessFullProjectPayroll.normalize(Collections.emptyMap()));
        Map<Long,Map<String,Object>> weights=Collections.singletonMap(20L,period(20,0));
        assertThrows(ServiceException.class,()->BusinessFullProjectPayroll.normalize(weights));
        Map<String,Object> pending=period(20,20);pending.put("confirmationStatus","PENDING");
        assertThrows(ServiceException.class,()->BusinessFullProjectPayroll.normalize(Collections.singletonMap(20L,pending)));
    }
    @Test void midMonthCostVersionsAccumulateRegionalDailyCostsAcrossTheWholeMonth(){
        rate.put("effectiveTo","2026-09-15");Map<String,Object> later=new LinkedHashMap<>(rate);later.remove("effectiveTo");later.put("effectiveFrom","2026-09-16");later.put("unitCost",new BigDecimal("22500.00"));
        BusinessFullProjectPayroll payroll=payroll(Arrays.asList(period(20,20)),Collections.singletonMap(20L,scope("2026-09-23")));
        Map<String,Object> cost=payroll.amount(20L,LocalDate.of(2026,9,29),calendar,Arrays.asList(rate,later),Collections.emptyList(),"CNY",null);
        assertEquals(new BigDecimal("17068.92"),cost.get("fullMonthlyCost"));
    }
}
