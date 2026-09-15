package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.ruoyi.business.mapper.BusinessProfitTaxMapper;
import com.ruoyi.common.exception.ServiceException;

/** Company-configured profit deduction, after all recorded project costs. */
@Service
public class BusinessProfitTaxService {
    @Autowired private BusinessProfitTaxMapper mapper;
    public List<Map<String,Object>> settings(Long userId) { return mapper.selectCompanies(userId); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public List<Map<String,Object>> save(Long companyId,Map<String,Object> input,Long userId,String userName) {
        if(input==null||settings(userId).stream().noneMatch(c->String.valueOf(companyId).equals(String.valueOf(c.get("companyDeptId")))))
            throw new ServiceException("只有老板可以设置所管理公司的税率");
        if(mapper.lockCompany(companyId)==null)throw new ServiceException("公司不存在或已停用");
        BigDecimal rate;
        try { rate=new BigDecimal(String.valueOf(input.get("taxRate"))); }
        catch(RuntimeException e){throw new ServiceException("请填写有效税率");}
        if(rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0||rate.scale()>4)
            throw new ServiceException("税率须在0%至100%之间，最多四位小数");
        String reason=String.valueOf(input.getOrDefault("reason"," ")).trim();
        if(reason.isEmpty()||"null".equals(reason)||reason.length()>500)throw new ServiceException("请填写不超过500字的设置说明");
        Map<String,Object> old=mapper.selectPolicy(companyId);
        String version=String.valueOf(old==null?0:old.get("version"));
        if(!version.equals(String.valueOf(input.get("version"))))throw new ServiceException("税率已更新，请刷新后重试");
        Map<String,Object> values=new LinkedHashMap<>(input);
        values.put("companyDeptId",companyId);values.put("taxRate",rate);values.put("reason",reason);
        values.put("userId",userId);values.put("userName",userName);values.put("oldRate",old==null?null:old.get("taxRate"));
        mapper.savePolicy(values);mapper.insertEvent(values);return settings(userId);
    }

    public void freeze(Long projectId,String userName) {
        Map<String,Object> basis=mapper.selectProjectBasis(projectId);
        if(basis==null)return;
        BigDecimal profit=number(basis.get("pretaxProfit")),tax=tax(profit,number(basis.get("taxRate")));
        basis.put("taxAmount",tax);basis.put("afterTaxProfit",profit.subtract(tax));basis.put("userName",userName);
        mapper.insertSnapshot(basis);
    }

    static BigDecimal tax(BigDecimal profit,BigDecimal rate) {
        BigDecimal positive=profit.max(BigDecimal.ZERO);
        return positive.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
            .min(positive.setScale(2,RoundingMode.DOWN));
    }

    /** A day's deduction is the change in cumulative company/currency tax, so projects offset before tax. */
    static void calculateSeries(List<Map<String,Object>> rows) {
        rows.sort(Comparator.comparing((Map<String,Object> row)->String.valueOf(row.get("bizDate")))
            .thenComparing(BusinessProfitTaxService::taxGroup)
            .thenComparing(row->String.valueOf(row.get("projectId")))
            .thenComparing(row->String.valueOf(row.get("resultId"))));
        Map<String,BigDecimal> balances=new HashMap<>();
        for(int start=0;start<rows.size();) {
            Map<String,Object> first=rows.get(start);String group=taxGroup(first),date=String.valueOf(first.get("bizDate"));int end=start+1;
            while(end<rows.size()&&group.equals(taxGroup(rows.get(end)))&&date.equals(String.valueOf(rows.get(end).get("bizDate"))))end++;
            BigDecimal prior=balances.getOrDefault(group,BigDecimal.ZERO),dayProfit=BigDecimal.ZERO;
            for(int i=start;i<end;i++)dayProfit=dayProfit.add(number(rows.get(i).get("profitAmount")));
            BigDecimal next=prior.add(dayProfit),amount=tax(next,number(first.get("taxRate"))).subtract(tax(prior,number(first.get("taxRate"))));
            allocateTax(rows.subList(start,end),amount);balances.put(group,next);start=end;
        }
    }

    private static String taxGroup(Map<String,Object> row) {
        if("1".equals(String.valueOf(row.get("taxFrozen"))))return "F:"+row.get("projectId");
        return "C:"+row.get("companyDeptId")+":"+row.get("currency")+":"+number(row.get("taxRate")).stripTrailingZeros().toPlainString();
    }

    private static void allocateTax(List<Map<String,Object>> rows,BigDecimal total) {
        for(Map<String,Object> row:rows){row.put("taxAmount",BigDecimal.ZERO.setScale(2));row.put("afterTaxProfit",number(row.get("profitAmount")));}
        if(total.signum()==0)return;
        List<Map<String,Object>> candidates=new ArrayList<>();BigDecimal weight=BigDecimal.ZERO;
        for(Map<String,Object> row:rows){BigDecimal profit=number(row.get("profitAmount"));if(profit.signum()==total.signum()){candidates.add(row);weight=weight.add(profit.abs());}}
        if(candidates.isEmpty()){candidates.add(rows.get(rows.size()-1));weight=BigDecimal.ONE;}
        BigDecimal assigned=BigDecimal.ZERO;
        for(int i=0;i<candidates.size();i++){
            Map<String,Object> row=candidates.get(i);BigDecimal share=i==candidates.size()-1?total.subtract(assigned)
                :total.multiply(number(row.get("profitAmount")).abs()).divide(weight,2,RoundingMode.DOWN);
            row.put("taxAmount",share);row.put("afterTaxProfit",number(row.get("profitAmount")).subtract(share));assigned=assigned.add(share);
        }
    }

    @SuppressWarnings("unchecked")
    public void decorate(Map<String,Object> result,Map<String,Object> query) {
        List<Map<String,Object>> rows=mapper.selectSeries(query);
        calculateSeries(rows);
        Map<String,Map<String,Object>> byResult=new HashMap<>();
        List<Map<String,Object>> departmentAdjustments=new ArrayList<>();
        Map<String,Totals> currencies=new LinkedHashMap<>(),projects=new HashMap<>(),companies=new HashMap<>();
        Totals all=new Totals();
        String from=String.valueOf(query.getOrDefault("dateFrom",""));
        for(Map<String,Object> row:rows) {
            if(!"null".equals(from)&&!from.isEmpty()&&String.valueOf(row.get("bizDate")).compareTo(from)<0)continue;
            if(row.get("resultId")!=null)byResult.put(String.valueOf(row.get("resultId")),row);
            if(number(row.get("isAdjustment")).signum()!=0)departmentAdjustments.add(new LinkedHashMap<>(row));
            all.add(row);
            currencies.computeIfAbsent(String.valueOf(row.get("currency")),k->new Totals()).add(row);
            projects.computeIfAbsent(String.valueOf(row.get("projectId")),k->new Totals()).add(row);
            companies.computeIfAbsent(row.get("companyDeptId")+":"+row.get("currency"),k->new Totals()).add(row);
        }
        for(String name:Arrays.asList("summary","today")) if(result.get(name) instanceof Map){Map<String,Object> summary=(Map<String,Object>)result.get(name);apply(summary,all);if(currencies.size()>1){summary.put("taxAmount",null);summary.put("afterTaxProfit",null);summary.put("pretaxProfit",null);}}
        for(String name:Arrays.asList("summaryByCurrency","todayByCurrency")) if(result.containsKey(name)) {
            List<Map<String,Object>> groups=new ArrayList<>(list(result.get(name)));
            for(String currency:currencies.keySet()) if(groups.stream().noneMatch(row->currency.equals(String.valueOf(row.get("currency"))))) {Map<String,Object> extra=new HashMap<>();extra.put("currency",currency);for(String field:Arrays.asList("profitAmount","revenueAmount","businessCost","personnelCost","bonusCost","publicCost","costAmount","adjustmentAmount","resultCount"))extra.put(field,BigDecimal.ZERO);groups.add(extra);}
            for(Map<String,Object> row:groups)apply(row,currencies.get(String.valueOf(row.get("currency"))));result.put(name,groups);
        }
        for(Map<String,Object> row:list(result.get("results"))) {
            Map<String,Object> calc=byResult.get(String.valueOf(row.get("resultId")));
            if(calc!=null)for(String field:Arrays.asList("taxRate","taxConfigured","taxFrozen","taxAmount","afterTaxProfit"))row.put(field,calc.get(field));
        }
        for(Map<String,Object> row:list(result.get("ranking")))apply(row,projects.get(String.valueOf(row.get("projectId"))));
        for(Map<String,Object> row:list(result.get("companies")))apply(row,companies.get(row.get("companyDeptId")+":"+row.get("currency")));
        for(Map<String,Object> row:list(result.get("closedAdjustmentTotals"))){row.put("includedInAfterTaxProfit",true);}
        result.put("departmentAdjustments",departmentAdjustments);
        result.put("taxUnconfiguredCount",all.missing.size());
        result.put("hasClosedAdjustments",all.hasAdjustments);
        if(all.hasAdjustments&&"NO_DATA".equals(result.get("dataStatus")))result.put("dataStatus","AVAILABLE");
        if(result.get("publicExpenseReference") instanceof Map) {
            Map<String,Object> reference=(Map<String,Object>)result.get("publicExpenseReference");
            for(Map<String,Object> row:list(reference.get("rows")))apply(row,projects.get(String.valueOf(row.get("projectId"))));
            for(Map<String,Object> row:list(reference.get("byCurrency")))apply(row,currencies.get(String.valueOf(row.get("currency"))));
        }
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> list(Object value){return value instanceof List?(List<Map<String,Object>>)value:Collections.emptyList();}
    private static void apply(Map<String,Object> row,Totals total) {
        if(total==null)total=new Totals();
        Object original=row.containsKey("profitAmount")?row.get("profitAmount"):row.get("referenceProfit");
        BigDecimal pretax=original==null?null:number(original).add(total.adjustments);
        row.put("pretaxProfit",pretax);row.put("taxAmount",pretax==null?null:total.amount);row.put("afterTaxProfit",pretax==null?null:pretax.subtract(total.amount));
        row.put("taxUnconfiguredCount",total.missing.size());
    }
    private static class Totals {
        BigDecimal amount=BigDecimal.ZERO,adjustments=BigDecimal.ZERO;Set<String> missing=new HashSet<>();boolean hasAdjustments;
        void add(Map<String,Object> row){amount=amount.add(number(row.get("taxAmount")));if(number(row.get("isAdjustment")).signum()!=0){adjustments=adjustments.add(number(row.get("profitAmount")));hasAdjustments=true;}if(!"1".equals(String.valueOf(row.get("taxConfigured")))&&!"1".equals(String.valueOf(row.get("taxFrozen"))))missing.add(String.valueOf(row.get("projectId")));}
    }
    private static BigDecimal number(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(value));}
}
