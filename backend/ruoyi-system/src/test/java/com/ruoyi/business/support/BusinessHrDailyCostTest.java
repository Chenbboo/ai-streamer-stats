package com.ruoyi.business.support;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;

class BusinessHrDailyCostTest {
    Map<String,Object> rate(Object salary,Object days){Map<String,Object> r=new HashMap<>();r.put("costMode","MONTHLY");r.put("unitCost",salary);r.put("standardWorkDays",days);return r;}
    Map<String,Object> calendar(){Map<String,Object> c=new HashMap<>();c.put("workingWeekdays","1,2,3,4,5");c.put("dailyMinutes",480);return c;}
    @Test void hrSalaryAndProjectCostsUseTheSameRegionalDivisor(){
        Map<String,Object> r=rate(7000,"21.75");
        assertEquals(new BigDecimal("321.8391"),BusinessHrDailyCost.dailyRate(r));
        assertEquals(new BigDecimal("321.84"),BusinessHrDailyCost.amount(r,calendar(),LocalDate.parse("2026-10-01"),new BigDecimal("100")));
        assertEquals(new BigDecimal("321.84"),BusinessHrDailyCost.amount(r,calendar(),LocalDate.parse("2026-09-01"),new BigDecimal("100")));
    }
    @Test void allocationMultipliesFourDecimalHrSalaryBeforeRoundingToCents(){
        assertEquals(new BigDecimal("151.7241"),BusinessHrDailyCost.dailyRate(rate(3300,"21.75")));
        assertEquals(new BigDecimal("91.03"),BusinessHrDailyCost.allocated(BusinessHrDailyCost.dailyRate(rate(3300,"21.75")),new BigDecimal("60")));
        assertEquals(new BigDecimal("75.86"),BusinessHrDailyCost.allocated(BusinessHrDailyCost.dailyRate(rate(3300,"21.75")),new BigDecimal("50")));
    }
    @Test void vietnamUsesTwentySixDaysAndChinaKeepsTwentyOnePointSeventyFive(){
        assertEquals(new BigDecimal("126.9231"),BusinessHrDailyCost.dailyRate(rate(3300,26)));
        assertEquals(new BigDecimal("151.7241"),BusinessHrDailyCost.dailyRate(rate(3300,22)));
        Map<String,Object> vietnam=rate(2600,21.75);vietnam.put("countryRegion","VN");
        assertEquals(new BigDecimal("100.0000"),BusinessHrDailyCost.dailyRate(vietnam));
        assertEquals(new BigDecimal("50.00"),BusinessHrDailyCost.amount(vietnam,calendar(),LocalDate.parse("2026-10-01"),new BigDecimal("50")));
        Map<String,Object> china=rate(3300,26);china.put("countryRegion","CN");
        assertEquals(new BigDecimal("151.7241"),BusinessHrDailyCost.dailyRate(china));
    }
    @Test void nonWorkingDatesHaveNoProjectCost(){
        assertEquals(new BigDecimal("0.00"),BusinessHrDailyCost.amount(rate(7000,"21.75"),calendar(),LocalDate.parse("2026-10-03"),new BigDecimal("100")));
    }
    @Test void storedDivisorCannotOverrideTheFixedRule(){
        for(Object divisor:Arrays.asList(null,0,-1,"invalid"))assertEquals(new BigDecimal("151.7241"),BusinessHrDailyCost.dailyRate(rate(3300,divisor)));
        assertThrows(ServiceException.class,()->BusinessHrDailyCost.dailyRate(rate(null,21.75)));
        assertThrows(ServiceException.class,()->BusinessHrDailyCost.dailyRate(rate(-1,21.75)));
    }
    @Test void completeMonthsAccumulateRegionalDailyCostForTheirBillableDays(){
        for(String month:Arrays.asList("2026-02","2026-07","2026-10")){
            java.time.YearMonth period=java.time.YearMonth.parse(month);
            BigDecimal total=BigDecimal.ZERO,half=BigDecimal.ZERO;int days=0;
            for(LocalDate d=period.atDay(1);!d.isAfter(period.atEndOfMonth());d=d.plusDays(1)){
                total=total.add(BusinessHrDailyCost.amount(rate(3300,21.75),calendar(),d,new BigDecimal("100")));
                half=half.add(BusinessHrDailyCost.amount(rate(3300,21.75),calendar(),d,new BigDecimal("50")));
                if(d.getDayOfWeek().getValue()<=5)days++;
            }
            assertEquals(new BigDecimal("151.72").multiply(BigDecimal.valueOf(days)),total,month);
            assertEquals(new BigDecimal("75.86").multiply(BigDecimal.valueOf(days)),half,month);
        }
    }
    @Test void sharedRoundingPreventsMultipleProjectsFromCreatingExtraPayrollCents(){
        com.ruoyi.business.support.BusinessPersonnelCost pricing=new com.ruoyi.business.support.BusinessPersonnelCost();
        Map<String,Object> r=rate("3300.03",21.75),c=calendar();
        BigDecimal total=BigDecimal.ZERO;
        for(LocalDate d=LocalDate.parse("2026-10-01");!d.isAfter(LocalDate.parse("2026-10-31"));d=d.plusDays(1)){
            if(!com.ruoyi.business.support.BusinessPersonnelCost.workingDay(c,d))continue;
            BigDecimal first=d.getDayOfMonth()<16?new BigDecimal("33.33"):new BigDecimal("50");
            BigDecimal second=new BigDecimal("100").subtract(first);
            BigDecimal a=pricing.projectAmount(r,c,d,first,BigDecimal.ZERO);
            BigDecimal b=pricing.projectAmount(r,c,d,second,first);
            assertEquals(pricing.amount(r,c,d,new BigDecimal("100")),a.add(b));
            total=total.add(a).add(b);
        }
        assertEquals(new BigDecimal("3338.06"),total);
    }
}
