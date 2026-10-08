package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.alibaba.fastjson2.JSON;
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
        Map<Long,BigDecimal> totals=new TreeMap<>(),personnelTotals=new TreeMap<>(),combinedTotals=new TreeMap<>();
        boolean departmentNet=BusinessPublicPersonnelService.departmentNet(bill);
        Map<String,Object> snapshot=departmentNet?JSON.parseObject(String.valueOf(bill.get("personnelSnapshot"))):Collections.emptyMap();
        if(Arrays.asList("PUBLISHED","SETTLED").contains(bill.get("status")))
        {
            for(Map<String,Object> owner:mapper.selectOwnerAllocations(billId))
                if("SUBMITTED".equals(owner.get("status")))
                    for(Map<String,Object> allocation:mapper.selectProjectAllocations(number(owner.get("allocationId"))))
                        {
                            totals.merge(number(allocation.get("projectId")),money(allocation.get("amount")),BigDecimal::add);
                            if("PERSONNEL".equals(owner.get("costPool")))personnelTotals.merge(number(allocation.get("projectId")),money(allocation.get("amount")),BigDecimal::add);
                            if("COMBINED".equals(owner.get("costPool")))combinedTotals.merge(number(allocation.get("projectId")),money(allocation.get("amount")),BigDecimal::add);
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
        // Combined allocations carry every source. Only their net IT fraction is
        // credited to IT projects; subsequent ordinary adjustments are excluded.
        BigDecimal retained=BigDecimal.ZERO;for(Map<String,Object> row:mapper.selectRetainedDailyCosts(billId))retained=retained.add(money(row.get("amount")));
        Map<Long,BigDecimal> itRecipients=combinedTotals.isEmpty()?itRecipients(snapshot,personnelTotals):itRecipients(snapshot,combinedTotals,money(bill.get("totalAmount")).subtract(retained));
        Map<String,BigDecimal> itDates=new TreeMap<>();
        for(Long projectId:projectIds)
        {
            BusinessProject project=projects.selectProjectByIdForUpdate(projectId);
            List<Map<String,Object>> prior=new ArrayList<>();
            for(Map<String,Object> row:previous)if(projectId.equals(number(row.get("projectId"))))prior.add(row);
            if(project==null||"CLOSED".equals(project.getAccountingState()))
            {
                if(departmentNet&&!confirmed&&itRecipients.getOrDefault(projectId,BigDecimal.ZERO).signum()>0)
                    throw new ServiceException("IT亏损承担项目已关账或删除，请退回重新分配");
                for(Map<String,Object> row:prior)desired.put(key(row),row);
                if(!prior.isEmpty())addTransferDates(itDates,itRecipients.getOrDefault(projectId,BigDecimal.ZERO),
                    LocalDate.parse(String.valueOf(prior.get(0).get("bizDate"))),LocalDate.parse(String.valueOf(prior.get(prior.size()-1).get("bizDate"))));
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
            for(Map<String,Object> row:combinedSchedule(billId,projectId,totals.get(projectId),departmentNet?BigDecimal.ZERO:personnel,start,end,confirmed))desired.put(key(row),row);
            addTransferDates(itDates,itRecipients.getOrDefault(projectId,BigDecimal.ZERO),start,end);
        }
        for(Map<String,Object> credit:transferCredits(billId,snapshot,itDates,confirmed)) {
            Long projectId=number(credit.get("projectId"));BusinessProject source=projects.selectProjectByIdForUpdate(projectId);
            if(source==null||"2".equals(source.getDelFlag())||"CLOSED".equals(source.getAccountingState())) {
                Map<String,Object> before=old.get(key(credit));
                if(!confirmed||before==null||money(before.get("itTransferAmount")).compareTo(money(credit.get("itTransferAmount")))!=0)
                    throw new ServiceException("IT亏损来源项目已关账或删除，不能新增转分");
                desired.put(key(credit),before);continue;
            }
            Map<String,Object> positive=desired.get(key(credit));
            if(positive!=null)credit.put("amount",money(positive.get("amount")).subtract(money(positive.get("itTransferAmount"))).add(money(credit.get("amount"))));
            desired.put(key(credit),credit);
        }
        Map<Long,SortedSet<String>> changed=new TreeMap<>();
        for(Map<String,Object> row:old.values())if(!desired.containsKey(key(row)))
        {mapper.deleteDailyRow(row);changed(changed,row);}
        for(Map<String,Object> row:desired.values())
        {
            Map<String,Object> before=old.get(key(row));
            if(before==null||money(before.get("amount")).compareTo(money(row.get("amount")))!=0||!Objects.equals(before.get("status"),row.get("status"))
                ||money(before.get("itTransferAmount")).compareTo(money(row.get("itTransferAmount")))!=0)
            {mapper.upsertDailyRow(row);changed(changed,row);}
        }
        for(Map<String,Object> row:mapper.selectUnrecognizedDailyDates(billId))changed(changed,row);
        for(Map.Entry<Long,SortedSet<String>> entry:changed.entrySet())for(String date:entry.getValue())
            accounting.recalculatePublicExpenseCost(entry.getKey(),java.sql.Date.valueOf(date),"public-expense-daily");
    }

    static Map<Long,BigDecimal> itRecipients(Map<String,Object> snapshot,Map<Long,BigDecimal> personnelTotals) {
        return itRecipients(snapshot,personnelTotals,money(snapshot.get("publicAmount")));
    }
    static Map<Long,BigDecimal> itRecipients(Map<String,Object> snapshot,Map<Long,BigDecimal> recipients,BigDecimal pool) {
        Map<Long,BigDecimal> out=new TreeMap<>();
        if(!(snapshot.get("itLoss") instanceof Map))return out;
        BigDecimal net=money(((Map<?,?>)snapshot.get("itLoss")).get("netLoss"));
        if(net.signum()==0)return out;
        if(pool.signum()<=0||net.compareTo(pool)>0)throw new ServiceException("IT亏损来源金额与分摊池不一致");
        BigDecimal cumulative=BigDecimal.ZERO,allocated=BigDecimal.ZERO;
        for(Map.Entry<Long,BigDecimal> item:new TreeMap<>(recipients).entrySet()) {
            cumulative=cumulative.add(item.getValue());
            if(cumulative.compareTo(pool)>0)throw new ServiceException("IT亏损转分超过来源金额");
            BigDecimal through=net.multiply(cumulative).divide(pool,2,RoundingMode.HALF_UP);
            out.put(item.getKey(),through.subtract(allocated));allocated=through;
        }
        return out;
    }
    private static void addTransferDates(Map<String,BigDecimal> dates,BigDecimal amount,LocalDate start,LocalDate end) {
        if(amount.signum()==0)return;
        for(Map<String,Object> row:schedule(0L,0L,amount,start,end,false))dates.merge(String.valueOf(row.get("bizDate")),money(row.get("amount")),BigDecimal::add);
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> transferCredits(Long billId,Map<String,Object> snapshot,Map<String,BigDecimal> dates,boolean confirmed) {
        List<Map<String,Object>> out=new ArrayList<>();
        if(!(snapshot.get("itLoss") instanceof Map)||dates.isEmpty())return out;
        Map<String,Object> it=(Map<String,Object>)snapshot.get("itLoss");
        BigDecimal net=money(it.get("netLoss"));List<Map<String,Object>> transfers=(List<Map<String,Object>>)it.get("transfers");
        BigDecimal total=BigDecimal.ZERO;for(Map<String,Object> transfer:transfers)total=total.add(money(transfer.get("amount")));
        if(net.signum()<=0||total.compareTo(net)!=0)throw new ServiceException("IT亏损转出金额与净亏损不一致");
        Map<Long,BigDecimal> remaining=new LinkedHashMap<>();for(Map<String,Object> transfer:transfers)remaining.put(number(transfer.get("projectId")),money(transfer.get("amount")));
        BigDecimal remainingTotal=net;
        for(Map.Entry<String,BigDecimal> date:dates.entrySet()) {
            if(date.getValue().compareTo(remainingTotal)>0)throw new ServiceException("IT亏损转分超过来源金额");
            if(date.getValue().signum()==0)continue;
            BigDecimal cumulative=BigDecimal.ZERO,allocated=BigDecimal.ZERO;
            for(Map<String,Object> transfer:transfers) {
                Long projectId=number(transfer.get("projectId"));BigDecimal capacity=remaining.get(projectId);
                cumulative=cumulative.add(capacity);
                BigDecimal through=date.getValue().multiply(cumulative).divide(remainingTotal,2,RoundingMode.HALF_UP);
                BigDecimal credit=through.subtract(allocated).negate();allocated=through;
                remaining.put(projectId,capacity.add(credit));
                if(credit.signum()==0)continue;
                Map<String,Object> row=new LinkedHashMap<>();row.put("billId",billId);row.put("projectId",transfer.get("projectId"));
                row.put("bizDate",date.getKey());row.put("amount",credit);row.put("itTransferAmount",credit);
                row.put("status",confirmed?"CONFIRMED":"ESTIMATED");out.add(row);
            }
            remainingTotal=remainingTotal.subtract(date.getValue());
        }
        return out;
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
