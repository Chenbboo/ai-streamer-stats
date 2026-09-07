package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;

/** Durable consumer: a pricing error never rolls back an independently confirmed work fact. */
@Service
@EnableScheduling
public class BusinessProjectWorkPricingService
{
    @Autowired private BusinessProjectWorkMapper mapper;
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private IBusinessAccountingService accountingService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper json;

    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void consumePending()
    {
        for(Map<String,Object> event:mapper.selectPendingEvents())process(id(event.get("eventId")),"work-pricing");
    }

    public Map<String,Object> retryProject(Long projectId,Long actorId,String userName,boolean admin)
    {
        BusinessProject p=projectMapper.selectProjectById(projectId);
        if(p==null)throw new ServiceException("项目不存在");
        Long sponsor=p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();
        if(!admin&&!actorId.equals(sponsor))throw new ServiceException("无权处理该项目的内部成本");
        BusinessProjectLifecycle.requireAccountingOpen(p);
        int processed=0;for(Map<String,Object> event:mapper.selectPendingProjectEvents(projectId)){process(id(event.get("eventId")),userName);processed++;}
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("processedEvents",processed);result.put("pendingCostCount",mapper.countPendingCosts(projectId));return result;
    }

    public void process(final Long eventId,final String userName)
    {
        TransactionTemplate tx=new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try{tx.execute(status->{processInTransaction(eventId,userName);return null;});}
        catch(Exception ex){
            // No exception text: JDBC/application errors can include sensitive values. The pending event remains retryable.
            Map<String,Object> failed=new HashMap<String,Object>();failed.put("eventId",eventId);failed.put("status","PENDING");failed.put("lastError","核算处理失败，等待重试");
            tx.execute(status->{mapper.recordEventFailure(failed);return null;});
        }
    }

    private void processInTransaction(Long eventId,String userName)
    {
        // Project first, then event: same ordering as confirmation and accounting close.
        Map<String,Object> hint=mapper.selectEvent(eventId);
        if(hint==null||"DONE".equals(hint.get("status")))return;
        BusinessProject project=projectMapper.selectProjectByIdForUpdate(id(hint.get("projectId")));
        BusinessProjectLifecycle.requireAccountingOpen(project);
        Map<String,Object> event=mapper.selectEventForUpdate(eventId);
        if(event==null||"DONE".equals(event.get("status")))return;
        Map<String,Object> entry=mapper.selectEntryForUpdate(id(event.get("entryId")));
        if(entry==null)throw new ServiceException("工作来源记录不存在");
        if(!"CONFIRMED".equals(entry.get("status"))||!"1".equals(entry.get("isCurrent"))){finish(event,"DONE",null);return;}
        Map<String,Object> cost=new LinkedHashMap<String,Object>();cost.put("entryId",entry.get("entryId"));cost.put("projectId",entry.get("projectId"));cost.put("userId",entry.get("userId"));cost.put("bizDate",entry.get("bizDate"));cost.put("currency",project.getBaseCurrency());
        Map<String,Object> basis=new LinkedHashMap<String,Object>();basis.put("costPolicyVersion","ACTUAL_WORK_V1");basis.put("sourceEntryId",entry.get("entryId"));basis.put("sourceRevision",entry.get("revisionNo"));basis.put("logicalEntryId",entry.get("logicalEntryId"));basis.put("workMinutes",entry.get("workMinutes"));basis.put("minutesPerDay",entry.get("minutesPerDay"));basis.put("inputUnit",entry.get("inputUnit"));basis.put("inputQuantity",entry.get("inputQuantity"));basis.put("unitSnapshotJson",entry.get("unitSnapshotJson"));basis.put("roundingMode","HALF_UP");basis.put("amountScale",2);
        List<Map<String,Object>> rates=mapper.selectApplicableRates(id(entry.get("userId")),day(entry.get("bizDate")));
        String pending=null;BigDecimal amount=null;
        if(integer(entry.get("workMinutes"))==0){amount=BigDecimal.ZERO.setScale(2);basis.put("formula","CORRECTION_WITHDRAWAL");}
        else if(rates==null||rates.size()!=1)pending=rates==null||rates.isEmpty()?"MISSING_RATE":"AMBIGUOUS_RATE";
        else {
            Map<String,Object> rate=rates.get(0);basis.put("rateSnapshot",rate);
            if(!project.getBaseCurrency().equalsIgnoreCase(String.valueOf(rate.get("currency"))))pending="CURRENCY_MISMATCH";
            else try{amount=price(integer(entry.get("workMinutes")),integer(entry.get("minutesPerDay")),rate);cost.put("ratePolicyId",rate.get("policyId"));cost.put("ratePolicyVersion",rate.get("version"));basis.put("formula","confirmedMinutes / rateUnitMinutes * effectiveRate; per-entry HALF_UP(2)");}catch(ServiceException ex){pending="INVALID_RATE";}
        }
        cost.put("pricingStatus",pending==null?"PRICED":"PENDING");cost.put("amount",amount);basis.put("pricingIssue",pending);basis.put("amount",amount);
        try{cost.put("basisJson",json.writeValueAsString(basis));}catch(Exception ex){throw new ServiceException("计价依据无法保存");}
        mapper.upsertWorkCost(cost);
        if(pending!=null){finish(event,"PENDING",pending);return;}
        accountingService.recalculatePersonnelCost(project.getProjectId(),DateUtils.parseDate(day(entry.get("bizDate"))),userName);
        finish(event,"DONE",null);
    }

    public static BigDecimal price(int minutes,int minutesPerDay,Map<String,Object> rate)
    {
        if(minutes<0||minutesPerDay<=0)throw new ServiceException("工作量换算无效");
        BigDecimal unit;
        try{unit=new BigDecimal(String.valueOf(rate.get("unitCost")));}catch(Exception ex){throw new ServiceException("缺少有效内部费率");}
        if(unit.signum()<0)throw new ServiceException("内部费率无效");
        String mode=String.valueOf(rate.get("costMode"));BigDecimal denominator;
        int rateMinutes=rate.get("rateMinutesPerDay")==null?480:((Number)rate.get("rateMinutesPerDay")).intValue();
        if(rateMinutes<1||rateMinutes>1440)throw new ServiceException("费率人天基准无效");
        if("HOURLY".equals(mode))denominator=new BigDecimal("60");
        else if("DAILY".equals(mode))denominator=BigDecimal.valueOf(rateMinutes);
        else if("MONTHLY".equals(mode)){
            BigDecimal days;try{days=new BigDecimal(String.valueOf(rate.get("standardWorkDays")));}catch(Exception ex){throw new ServiceException("缺少月度标准工作天数");}
            if(days.signum()<=0)throw new ServiceException("月度标准工作天数无效");denominator=days.multiply(BigDecimal.valueOf(rateMinutes));
        }else throw new ServiceException("实际工时不支持该内部费率单位");
        return unit.multiply(BigDecimal.valueOf(minutes)).divide(denominator,2,RoundingMode.HALF_UP);
    }
    private void finish(Map<String,Object> event,String status,String reason){event.put("status",status);event.put("lastError",reason);mapper.finishEvent(event);}
    private static Long id(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
    private static int integer(Object v){return ((Number)v).intValue();}
    private static String day(Object v){return v instanceof Date?DateUtils.parseDateToStr("yyyy-MM-dd",(Date)v):String.valueOf(v).substring(0,10);}
}
