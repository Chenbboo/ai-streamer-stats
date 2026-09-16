package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessPublicExpenseMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Daily schedules include future dates; only elapsed dates become operating costs. */
@Service
public class BusinessPublicExpenseDailyService
{
    @Autowired private BusinessPublicExpenseMapper mapper;
    @Autowired private BusinessProjectMapper projects;
    @Autowired @Lazy private IBusinessAccountingService accounting;

    public List<Long> bills() { return mapper.selectDailyBills(); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void synchronize(Long billId)
    {
        Map<String,Object> first=mapper.selectBill(billId);
        if(first==null||!"DAILY_V1".equals(first.get("recognitionMode")))return;
        mapper.selectCompanyForUpdate(number(first.get("companyDeptId")));
        Map<String,Object> bill=mapper.selectBillForUpdate(billId);
        List<Map<String,Object>> previous=mapper.selectDailyRows(billId);
        Map<Long,BigDecimal> totals=new TreeMap<>(),personnelTotals=new TreeMap<>();
        if(Arrays.asList("PUBLISHED","SETTLED").contains(bill.get("status")))
        {
            for(Map<String,Object> owner:mapper.selectOwnerAllocations(billId))
                if("SUBMITTED".equals(owner.get("status")))
                    for(Map<String,Object> allocation:mapper.selectProjectAllocations(number(owner.get("allocationId"))))
                        {
                            totals.merge(number(allocation.get("projectId")),money(allocation.get("amount")),BigDecimal::add);
                            if("PERSONNEL".equals(owner.get("costPool")))personnelTotals.merge(number(allocation.get("projectId")),money(allocation.get("amount")),BigDecimal::add);
                        }
            for(Map<String,Object> adjustment:mapper.selectAdjustments(billId,null))
                totals.merge(number(adjustment.get("projectId")),money(adjustment.get("amount")),BigDecimal::add);
        }
        Set<Long> projectIds=new TreeSet<>(totals.keySet());
        for(Map<String,Object> row:previous)projectIds.add(number(row.get("projectId")));
        Map<String,Map<String,Object>> desired=new LinkedHashMap<>(),old=new LinkedHashMap<>();
        for(Map<String,Object> row:previous)old.put(key(row),row);
        YearMonth month=YearMonth.parse(String.valueOf(bill.get("month")));
        boolean confirmed="SETTLED".equals(bill.get("status"));
        for(Long projectId:projectIds)
        {
            BusinessProject project=projects.selectProjectByIdForUpdate(projectId);
            List<Map<String,Object>> prior=new ArrayList<>();
            for(Map<String,Object> row:previous)if(projectId.equals(number(row.get("projectId"))))prior.add(row);
            if(project==null||"CLOSED".equals(project.getAccountingState()))
            {
                for(Map<String,Object> row:prior)desired.put(key(row),row);
                continue;
            }
            if(!totals.containsKey(projectId))continue;
            LocalDate start=month.atDay(1),end=month.atEndOfMonth();
            // Settled schedules retain their original dates even after project handover or plan edits.
            if(confirmed&&!prior.isEmpty()&&prior.stream().allMatch(row->"CONFIRMED".equals(row.get("status"))))
            {
                start=LocalDate.parse(String.valueOf(prior.get(0).get("bizDate")));
                end=LocalDate.parse(String.valueOf(prior.get(prior.size()-1).get("bizDate")));
            }
            else
            {
                LocalDate projectStart=day(project.getActualStartDate()==null?project.getPlanStartDate():project.getActualStartDate());
                LocalDate projectEnd=day(project.getActualEndDate()==null?project.getPlanEndDate():project.getActualEndDate());
                if(projectStart!=null&&projectStart.isAfter(start))start=projectStart;
                if(projectEnd!=null&&projectEnd.isBefore(end))end=projectEnd;
            }
            if(end.isBefore(start))throw new ServiceException("项目在该月没有可分摊日期，请核对项目起止日期");
            BigDecimal personnel=personnelTotals.getOrDefault(projectId,BigDecimal.ZERO);
            for(Map<String,Object> row:combinedSchedule(billId,projectId,totals.get(projectId),personnel,start,end,confirmed))desired.put(key(row),row);
        }
        Map<Long,SortedSet<String>> changed=new TreeMap<>();
        for(Map<String,Object> row:old.values())if(!desired.containsKey(key(row)))
        {mapper.deleteDailyRow(row);changed(changed,row);}
        for(Map<String,Object> row:desired.values())
        {
            Map<String,Object> before=old.get(key(row));
            if(before==null||money(before.get("amount")).compareTo(money(row.get("amount")))!=0||!Objects.equals(before.get("status"),row.get("status")))
            {mapper.upsertDailyRow(row);changed(changed,row);}
        }
        for(Map<String,Object> row:mapper.selectUnrecognizedDailyDates(billId))changed(changed,row);
        for(Map.Entry<Long,SortedSet<String>> entry:changed.entrySet())for(String date:entry.getValue())
            accounting.recalculatePublicExpenseCost(entry.getKey(),java.sql.Date.valueOf(date),"public-expense-daily");
    }

    static List<Map<String,Object>> combinedSchedule(Long billId,Long projectId,BigDecimal total,BigDecimal personnel,LocalDate start,LocalDate end,boolean confirmed) {
        // Final monthly cost replaces estimates, rather than adding another monthly charge.
        if(confirmed)return schedule(billId,projectId,total,start,end,true);
        List<Map<String,Object>> rows=schedule(billId,projectId,total.subtract(personnel),start,end,false);
        BigDecimal estimate=personnel.divide(new BigDecimal("21.75"),2,RoundingMode.HALF_UP);
        for(Map<String,Object> row:rows)row.put("amount",money(row.get("amount")).add(estimate));return rows;
    }

    static List<Map<String,Object>> schedule(Long billId,Long projectId,BigDecimal total,LocalDate start,LocalDate end,boolean confirmed)
    {
        if(total.signum()<0)throw new ServiceException("项目公共费用不能为负数");
        int days=Math.toIntExact(ChronoUnit.DAYS.between(start,end)+1);
        if(days<1||days>31)throw new ServiceException("公共费用分摊日期范围不正确");
        BigDecimal normal=total.divide(BigDecimal.valueOf(days),2,RoundingMode.DOWN);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<days;i++)
        {
            Map<String,Object> row=new LinkedHashMap<>();row.put("billId",billId);row.put("projectId",projectId);
            row.put("bizDate",start.plusDays(i).toString());row.put("status",confirmed?"CONFIRMED":"ESTIMATED");
            row.put("amount",i==days-1?total.subtract(normal.multiply(BigDecimal.valueOf(days-1))):normal);rows.add(row);
        }
        return rows;
    }
    private static void changed(Map<Long,SortedSet<String>> dates,Map<String,Object> row)
    {
        String date=String.valueOf(row.get("bizDate"));
        if(!LocalDate.parse(date).isAfter(LocalDate.now()))dates.computeIfAbsent(number(row.get("projectId")),id->new TreeSet<>()).add(date);
    }
    private static String key(Map<String,Object> row){return row.get("projectId")+":"+row.get("bizDate");}
    private static Long number(Object value){return Long.valueOf(String.valueOf(value));}
    private static BigDecimal money(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(value));}
    private static LocalDate day(Date value){return value==null?null:LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",value));}
}
