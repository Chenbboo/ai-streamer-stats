package com.ruoyi.business.support;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessPersonnelCostTest {
    Map<String,Object> calendar(){Map<String,Object> value=new HashMap<>();value.put("workingWeekdays","1,2,3,4,5");value.put("dailyMinutes",480);return value;}
    Map<String,Object> rate(String salary,String country){Map<String,Object> value=new HashMap<>();value.put("costMode","MONTHLY");value.put("unitCost",salary);value.put("countryRegion",country);return value;}
    @Test void completeMonthsUseExactMonthlySalaryAndSharesRegardlessOfWorkingDayCount(){
        for(String month:Arrays.asList("2026-02","2026-07","2026-10")){
            YearMonth period=YearMonth.parse(month);BusinessPersonnelCost pricing=new BusinessPersonnelCost();
            Map<String,Object> calendar=calendar(),rate=rate("7500.03","CN");BigDecimal total=BigDecimal.ZERO,first=BigDecimal.ZERO,second=BigDecimal.ZERO;
            for(LocalDate day=period.atDay(1);!day.isAfter(period.atEndOfMonth());day=day.plusDays(1)){
                BigDecimal a=pricing.projectAmount(rate,calendar,day,new BigDecimal("33.33"),BigDecimal.ZERO,true);
                BigDecimal b=pricing.projectAmount(rate,calendar,day,new BigDecimal("66.67"),new BigDecimal("33.33"),true);
                BigDecimal all=pricing.amount(rate,calendar,day,new BigDecimal("100"),true);
                assertEquals(all,a.add(b));total=total.add(all);first=first.add(a);second=second.add(b);
            }
            assertEquals(new BigDecimal("7500.03"),total,month);assertEquals(new BigDecimal("2499.76"),first,month);assertEquals(total,first.add(second));
        }
    }
    @Test void onlyCalendarMonthBoundariesQualifyAndPartialDaysUseRegionalSalary(){
        LocalDate october=LocalDate.parse("2026-10-09");
        assertFalse(BusinessPersonnelCost.fullMonth(october,october,LocalDate.parse("2026-11-10")));
        assertTrue(BusinessPersonnelCost.fullMonth(october,LocalDate.parse("2026-10-01"),LocalDate.parse("2026-11-10")));
        BusinessPersonnelCost pricing=new BusinessPersonnelCost();
        assertEquals(new BigDecimal("344.83"),pricing.amount(rate("7500","CN"),calendar(),october,new BigDecimal("100"),false));
        assertEquals(new BigDecimal("100.00"),pricing.amount(rate("2600","VN"),calendar(),october,new BigDecimal("100"),false));
    }
    @Test void monthlyRateAndWeightChangesAreProratedWithoutLosingCents(){
        BusinessPersonnelCost pricing=new BusinessPersonnelCost();Map<String,Object> calendar=calendar();BigDecimal total=BigDecimal.ZERO;
        for(LocalDate day=LocalDate.parse("2026-09-01");!day.isAfter(LocalDate.parse("2026-09-30"));day=day.plusDays(1))
            total=total.add(pricing.amount(rate(day.getDayOfMonth()<=15?"7500":"9000","CN"),calendar,day,new BigDecimal("50"),true));
        assertEquals(new BigDecimal("4125.00"),total);
    }
}
