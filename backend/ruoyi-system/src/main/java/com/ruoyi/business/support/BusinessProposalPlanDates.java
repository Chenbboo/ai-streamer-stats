package com.ruoyi.business.support;

import java.util.Date;
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
}
