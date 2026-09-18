package com.ruoyi.business.support;

import java.util.Date;
import java.util.Calendar;
import com.ruoyi.common.utils.DateUtils;

/** Shared date boundaries for proposal previews and persisted plan lines. */
public final class BusinessProposalPlanDates {
    private BusinessProposalPlanDates() { }

    public static String issue(Object value, Date start, Date end, String label, int row) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        Date date = DateUtils.parseDate(value);
        if (date == null || start != null && date.before(start) || end != null && date.after(end))
            return label + "第" + row + "行日期无效或超出项目起止日期";
        return null;
    }

    public static String afterStartMonthIssue(Object value, Date start, String label, int row) {
        if (value == null || String.valueOf(value).trim().isEmpty() || start == null) return null;
        Date date = DateUtils.parseDate(value);
        if (date == null) return label + "第" + row + "行日期无效";
        Calendar minimum = Calendar.getInstance();minimum.setTime(start);
        minimum.set(Calendar.DAY_OF_MONTH,1);minimum.add(Calendar.MONTH,1);
        if (date.before(minimum.getTime())) return label + "第" + row + "行完成月份须晚于项目开始月份";
        return null;
    }

    public static String revenueIssue(Object value, Date start, Date end, String label, int row) {
        return extendedMonthIssue(value, start, end, label, row, "收入");
    }

    public static String expenseIssue(Object value, Date start, Date end, String label, int row) {
        return extendedMonthIssue(value, start, end, label, row, "支出");
    }

    private static String extendedMonthIssue(Object value, Date start, Date end, String label, int row, String type) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        Date date = DateUtils.parseDate(value);
        if (date == null) return label + "第" + row + "行日期无效";
        Calendar minimum = monthBoundary(start, -6);
        Calendar maximum = monthBoundary(end, 7);
        if (minimum != null && date.before(minimum.getTime()) || maximum != null && !date.before(maximum.getTime()))
            return label + "第" + row + "行" + type + "月份须在项目开始月前6个月至" + (end == null ? "其后任意月份" : "结束月后6个月") + "内";
        return null;
    }

    private static Calendar monthBoundary(Date value, int offset) {
        if (value == null) return null;
        Calendar result = Calendar.getInstance();result.setTime(value);
        result.set(Calendar.DAY_OF_MONTH, 1);result.set(Calendar.HOUR_OF_DAY, 0);result.set(Calendar.MINUTE, 0);result.set(Calendar.SECOND, 0);result.set(Calendar.MILLISECOND, 0);
        result.add(Calendar.MONTH, offset);return result;
    }
}
