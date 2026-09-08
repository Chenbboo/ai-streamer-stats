package com.ruoyi.business.support;

import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.ruoyi.business.domain.BusinessProjectProposal;
import com.ruoyi.common.utils.DateUtils;

/** Recover old drafts whose staffing rows did not persist the selected mode. */
public final class BusinessProposalParticipation {
    private BusinessProposalParticipation() { }

    public static String mode(Map<String,Object> line, BusinessProjectProposal proposal) {
        Object value=line.get("participationMode");
        if(value!=null&&!String.valueOf(value).trim().isEmpty())
            return String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        Date from=date(line.get("planStartDate")),to=date(line.get("planEndDate"));
        if(line.get("planStartDate")!=null&&from==null||line.get("planEndDate")!=null&&to==null)return "CUSTOM";
        if(line.get("planStartDate")==null&&line.get("planEndDate")==null)return "FOLLOW_PROJECT";
        if(from!=null&&sameDay(from,proposal.getPlanStartDate())&&sameDay(to,proposal.getPlanEndDate()))return "FOLLOW_PROJECT";
        if(from!=null&&line.get("planEndDate")==null)return "UNLIMITED";
        return "CUSTOM";
    }

    public static Date date(Object value) {
        return value instanceof Date?(Date)value:DateUtils.parseDate(value);
    }

    private static boolean sameDay(Date a,Date b) {
        return Objects.equals(a==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",a),
            b==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",b));
    }
}
