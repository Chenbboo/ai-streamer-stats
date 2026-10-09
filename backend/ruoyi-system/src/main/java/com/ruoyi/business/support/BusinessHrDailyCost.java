package com.ruoyi.business.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;

/** Shared regional daily cost for HR display, estimates and project accounting. */
public final class BusinessHrDailyCost {
    public static final String RULE = "REGION_STANDARD_DAY_V2";
    public static final BigDecimal STANDARD_WORK_DAYS = new BigDecimal("21.75");
    public static final BigDecimal VIETNAM_WORK_DAYS = new BigDecimal("26");
    public static final String FORMULA = "月成本 ÷ 地区标准工作日（中国21.75天、越南26天） × 当日项目投入比例（按参与工作日逐日四舍五入到分后累计）";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private BusinessHrDailyCost() { }

    public static BigDecimal standardWorkDays(Object countryRegion, Object storedDays) {
        String region=countryRegion==null?"":String.valueOf(countryRegion).trim();
        if("VN".equalsIgnoreCase(region))return VIETNAM_WORK_DAYS;
        // Older policies without a country snapshot may still identify their Vietnam basis.
        if(region.isEmpty()||"null".equals(region))try {
            if(VIETNAM_WORK_DAYS.compareTo(new BigDecimal(String.valueOf(storedDays)))==0)return VIETNAM_WORK_DAYS;
        }catch(RuntimeException ignored) { }
        return STANDARD_WORK_DAYS;
    }

    public static BigDecimal standardWorkDays(Map<String,Object> rate) {
        return standardWorkDays(rate.get("countryRegion"),rate.get("standardWorkDays"));
    }

    public static BigDecimal dailyRate(String mode, Object unitCost, Object standardWorkDays) {
        return dailyRate(mode,unitCost,standardWorkDays,null);
    }

    public static BigDecimal dailyRate(String mode, Object unitCost, Object storedDays, Object countryRegion) {
        try {
            BigDecimal unit = new BigDecimal(String.valueOf(unitCost));
            if (unit.signum() < 0) throw new IllegalArgumentException();
            if ("MONTHLY".equals(mode)) {
                return unit.divide(standardWorkDays(countryRegion,storedDays), 4, RoundingMode.HALF_UP);
            }
            if ("HOURLY".equals(mode)) return unit.multiply(new BigDecimal("8")).setScale(4, RoundingMode.HALF_UP);
            if ("DAILY".equals(mode)) return unit.setScale(4, RoundingMode.HALF_UP);
        } catch (RuntimeException invalid) {
            throw new ServiceException("缺少有效的人力资源日薪或标准工作日数");
        }
        throw new ServiceException("缺少可折算的有效日成本");
    }

    public static BigDecimal dailyRate(Map<String,Object> rate) {
        return dailyRate(String.valueOf(rate.get("costMode")), rate.get("unitCost"), rate.get("standardWorkDays"),rate.get("countryRegion"));
    }

    public static BigDecimal allocated(BigDecimal dailyRate, BigDecimal percent) {
        if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0)
            throw new ServiceException("项目投入权重必须在0%至100%之间");
        return dailyRate.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal amount(Map<String,Object> rate, Map<String,Object> calendar, LocalDate date, BigDecimal percent) {
        if (!BusinessPersonnelCost.workingDay(calendar, date)) return BigDecimal.ZERO.setScale(2);
        if ("MONTHLY".equals(rate.get("costMode"))) return new BusinessPersonnelCost().amount(rate, calendar, date, percent);
        return allocated(dailyRate(rate), percent);
    }
}
