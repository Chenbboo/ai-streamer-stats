package com.ruoyi.business.support;

import java.time.LocalDate;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/** Dated membership/calendar inputs for a forecast, without reading or writing actual day costs. */
public final class BusinessPlannedMemberDays {
    private BusinessPlannedMemberDays() {}

    public static List<Map<String,Object>> days(Map<String,Object> staff,LocalDate from,LocalDate to) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)) {
            Map<String,Object> calendar=calendarOn(staff,day);
            if(calendar==null||!BusinessPersonnelCost.workingDay(calendar,day))continue;
            Map<String,Object> item=new LinkedHashMap<>();item.put("bizDate",day.toString());item.put("calendar",calendar);
            item.put("plannedMinutes",Integer.parseInt(String.valueOf(calendar.get("dailyMinutes"))));result.add(item);
        }
        return result;
    }
    public static boolean coversFullMonth(Map<String,Object> staff,LocalDate date) {
        java.time.YearMonth month=java.time.YearMonth.from(date);
        for(LocalDate day=month.atDay(1);!day.isAfter(month.atEndOfMonth());day=day.plusDays(1))
            if(calendarOn(staff,day)==null)return false;
        return true;
    }
    private static Map<String,Object> calendarOn(Map<String,Object> staff,LocalDate day) {
        Map<String,Object> member=null;
        for(Map<String,Object> period:rows(staff.get("membershipPeriods")))
            if(covers(period,"joinedDate","leftDate",day)) {member=period;break;}
        if(member==null)return null;
        String role=String.valueOf(member.get("memberRole"));
        for(Map<String,Object> period:rows(staff.get("rolePeriods")))
            if(period.get("effectiveFrom")!=null&&!day.isBefore(date(period.get("effectiveFrom"))))role=String.valueOf(period.get("memberRole"));
        if("OBSERVER".equals(role))return null;

        List<Map<String,Object>> plans=new ArrayList<>();
        for(Map<String,Object> plan:rows(staff.get("assignmentPeriods")))
            if("FOLLOW_PROJECT".equals(plan.get("participationMode"))||member.get("joinedDate")==null
                ||plan.get("effectiveTo")==null||!date(plan.get("effectiveTo")).isBefore(date(member.get("joinedDate"))))plans.add(plan);
        Map<String,Object> selected=null;
        for(Map<String,Object> plan:plans) {
            boolean active="ACTIVE".equals(plan.get("status"))||"RETIRED".equals(plan.get("status"))
                &&plan.get("retiredTime")!=null&&day.isBefore(date(plan.get("retiredTime")));
            boolean starts=plan.get("effectiveFrom")==null||!day.isBefore(date(plan.get("effectiveFrom")));
            if(active&&starts&&("FOLLOW_PROJECT".equals(plan.get("participationMode"))||covers(plan,"effectiveFrom","effectiveTo",day))) {selected=plan;break;}
        }
        if(!plans.isEmpty()&&selected==null)return null;
        Map<String,Object> calendar=null;
        for(Map<String,Object> candidate:rows(staff.get("calendars"))) {
            if(!covers(candidate,"effectiveFrom","effectiveTo",day))continue;
            if(selected!=null&&selected.get("calendarId")!=null&&!String.valueOf(selected.get("calendarId")).equals(String.valueOf(candidate.get("calendarId"))))continue;
            if(calendar==null||Long.parseLong(String.valueOf(candidate.get("calendarId")))<Long.parseLong(String.valueOf(calendar.get("calendarId"))))calendar=candidate;
        }
        if(calendar==null)throw new ServiceException(day+" 缺少有效工作日历");
        return calendar;
    }
    public static boolean covers(Map<String,Object> period,String from,String to,LocalDate day) {
        return (period.get(from)==null||!day.isBefore(date(period.get(from))))&&(period.get(to)==null||!day.isAfter(date(period.get(to))));
    }
    private static LocalDate date(Object value) {return LocalDate.parse(value instanceof java.util.Date?com.ruoyi.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd",(java.util.Date)value):String.valueOf(value).substring(0,10));}
    private static List<Map<String,Object>> rows(Object value) {return value instanceof List?(List<Map<String,Object>>)value:Collections.emptyList();}
}
