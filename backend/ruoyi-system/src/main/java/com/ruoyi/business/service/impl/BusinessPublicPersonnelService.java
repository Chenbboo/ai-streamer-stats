package com.ruoyi.business.service.impl;

import java.math.*;
import java.time.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.support.BusinessPersonnelCost;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Monthly source snapshot. Payroll details are returned only through the boss workspace. */
@Service
public class BusinessPublicPersonnelService {
    @Autowired private BusinessPublicExpenseMapper mapper;
    @Autowired private BusinessProjectMapper projects;
    @Autowired private BusinessProjectWorkMapper work;
    @Autowired private BusinessMemberDayCostMapper costs;
    @Autowired private BusinessMemberDayCostService memberCosts;

    public Map<String,Object> preview(Long company,String month,String currency) {
        YearMonth period=YearMonth.parse(month);
        LocalDate from=period.atDay(1),to=period.atEndOfMonth();
        List<Map<String,Object>> staff=mapper.selectPersonnelStaff(company,month),calendars=work.selectCalendars();
        Map<Long,Map<String,Object>> people=new LinkedHashMap<>();
        BusinessPersonnelCost pricing=new BusinessPersonnelCost();
        for(Map<String,Object> person:staff) {
            Long userId=id(person.get("userId"));
            List<Map<String,Object>> rates=work.selectBudgetRates(userId,from.toString(),to.toString());
            if(!rates.isEmpty()&&rates.stream().noneMatch(r->currency.equals(r.get("currency"))))continue;
            Set<String> issues=new LinkedHashSet<>();BigDecimal total=BigDecimal.ZERO;
            List<Map<String,Object>> details=new ArrayList<>();
            for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)) {
                if(person.get("hireDate")!=null&&date.isBefore(day(person.get("hireDate"))))continue;
                Map<String,Object> calendar=null;
                for(Map<String,Object> c:calendars)if(covers(c,date)&&(calendar==null||id(c.get("calendarId"))<id(calendar.get("calendarId"))))calendar=c;
                if(calendar==null){addIssue(issues,details,"缺少工作日历",date,null,null);continue;}
                if(!BusinessPersonnelCost.workingDay(calendar,date))continue;
                List<Map<String,Object>> matches=new ArrayList<>();for(Map<String,Object> rate:rates)if(covers(rate,date))matches.add(rate);
                if(matches.size()!=1){addIssue(issues,details,matches.isEmpty()?(rates.isEmpty()?"本月未设置人员成本":"部分工作日缺少有效人员成本"):"人员成本生效日期重叠",date,null,null);continue;}
                if(!currency.equals(matches.get(0).get("currency"))){addIssue(issues,details,"人员成本币种与所选币种不一致",date,null,null);continue;}
                total=total.add(pricing.amount(matches.get(0),calendar,date,new BigDecimal("100")));
            }
            if("LEFT".equals(person.get("employmentStatus")))addIssue(issues,details,"离职人员请核对实际月成本",null,null,null);
            Map<String,Object> row=new LinkedHashMap<>(person);
            row.put("calculatedAmount",issues.isEmpty()?total:null);row.put("totalAmount",issues.isEmpty()?total:null);
            row.put("projectAmount",BigDecimal.ZERO);row.put("businessFactIds",new ArrayList<>());row.put("reason","");
            row.put("issueDetails",details);row.put("projectIssueDetails",new ArrayList<Map<String,Object>>());
            row.put("issues",new ArrayList<>(issues));row.put("projectIssues",new ArrayList<String>());people.put(userId,row);
        }
        Set<String> issues=new LinkedHashSet<>();
        for(Long projectId:mapper.selectPersonnelProjects(company,month)) {
            BusinessProject p=projects.selectProjectById(projectId);if(p==null)continue;
            if(!BusinessMemberDayCostService.enabled(p)) {
                // A historical costing policy cannot be silently treated as zero direct labour.
                for(Map<String,Object> m:work.selectMembers(projectId))if(people.containsKey(id(m.get("userId"))))
                    issues.add(p.getProjectName()+" 使用历史人员核算方式，请先核对迁移后再启用公共人员分摊");
                continue;
            }
            LocalDate end=to;
            java.util.Date projectEnd=p.getActualEndDate()!=null?p.getActualEndDate():p.getPlanEndDate();
            if(projectEnd!=null&&day(projectEnd).isBefore(end))end=day(projectEnd);
            List<Map<String,Object>> direct="CLOSED".equals(p.getAccountingState())?costs.selectCosts(projectId):
                end.isBefore(from)?Collections.emptyList():memberCosts.calculate(p,from,end);
            for(Map<String,Object> cost:direct) {
                LocalDate date=day(cost.get("bizDate"));if(date.isBefore(from)||date.isAfter(to))continue;
                Map<String,Object> person=people.get(id(cost.get("userId")));if(person==null)continue;
                if(!currency.equals(cost.get("currency"))||!"PRICED".equals(cost.get("pricingStatus"))||cost.get("amount")==null) {
                    @SuppressWarnings("unchecked") List<String> pending=(List<String>)person.get("projectIssues");
                    String reason=currency.equals(cost.get("currency"))?projectIssue(cost):"项目人员成本币种不一致";
                    String issue="项目「"+p.getProjectName()+"」："+reason;
                    if(!pending.contains(issue))pending.add(issue);
                    @SuppressWarnings("unchecked") List<Map<String,Object>> details=(List<Map<String,Object>>)person.get("projectIssueDetails");
                    addIssue(null,details,reason,date,projectId,p.getProjectName());
                } else person.put("projectAmount",decimal(person.get("projectAmount")).add(decimal(cost.get("amount"))));
            }
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("rows",new ArrayList<>(people.values()));out.put("issues",new ArrayList<>(issues));
        out.put("businessFacts",mapper.selectPersonnelBusinessFacts(company,month,currency));
        out.put("month",month);out.put("currency",currency);out.put("estimated",!period.isBefore(YearMonth.now()));return out;
    }

    /** Carry forward only previously recorded expense links; payroll always comes from cost policies. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> automaticPreview(Long company,String month,String currency,Map<String,Object> previous) {
        Map<String,Object> source=preview(company,month,currency);
        Map<Long,Map<String,Object>> oldRows=new HashMap<>();
        if(previous!=null)for(Map<String,Object> row:(List<Map<String,Object>>)previous.get("rows"))oldRows.put(id(row.get("userId")),row);
        Map<Long,Map<String,Object>> facts=new HashMap<>();
        for(Map<String,Object> fact:(List<Map<String,Object>>)source.get("businessFacts"))facts.put(id(fact.get("factId")),fact);
        Set<Long> used=new HashSet<>();
        for(Map<String,Object> row:(List<Map<String,Object>>)source.get("rows")) {
            Map<String,Object> old=oldRows.remove(id(row.get("userId")));
            List<Long> links=new ArrayList<>();BigDecimal business=BigDecimal.ZERO;
            if(old!=null&&old.get("businessFactIds") instanceof List)for(Object value:(List<?>)old.get("businessFactIds")) {
                Long factId=id(value);Map<String,Object> fact=facts.get(factId);
                if(fact==null||!used.add(factId))throw new ServiceException("历史人员支出关联已变化，请先核对原支出记录");
                business=business.add(decimal(fact.get("amount")));links.add(factId);
            }
            row.put("businessFactIds",links);row.put("businessAmount",business);
            if(!links.isEmpty())row.put("reason",old.get("reason"));
            row.put("publicAmount",row.get("totalAmount")==null||!((List<?>)row.get("projectIssues")).isEmpty()?null:
                decimal(row.get("totalAmount")).subtract(decimal(row.get("projectAmount"))).subtract(business));
        }
        for(Map<String,Object> old:oldRows.values())if(old.get("businessFactIds") instanceof List&&!((List<?>)old.get("businessFactIds")).isEmpty())
            throw new ServiceException("历史人员支出对应的人员范围已变化，请先核对人员归属");
        return source;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> automaticSnapshot(Long company,String month,String currency,Map<String,Object> previous) {
        Map<String,Object> source=automaticPreview(company,month,currency,previous);
        List<Map<String,Object>> rows=(List<Map<String,Object>>)source.get("rows");
        if(rows.isEmpty())throw new ServiceException("本月没有可分摊的人员成本");
        for(Map<String,Object> row:rows)if(row.get("calculatedAmount")==null)
            throw new ServiceException(row.get("userName")+" 的月成本待完善，请到人员成本设置补齐金额和生效期间");
        Map<String,Object> result=buildSnapshot(source,rows);
        result.put("sourceMode","AUTOMATIC");return result;
    }

    /** Legacy snapshots remain readable and verifiable; new saves use automaticSnapshot only. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> snapshot(Long company,String month,String currency,List<Map<String,Object>> input) {
        return buildSnapshot(preview(company,month,currency),input);
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> buildSnapshot(Map<String,Object> source,List<Map<String,Object>> input) {
        if(!((List<?>)source.get("issues")).isEmpty())throw new ServiceException(String.join("；",(List<String>)source.get("issues")));
        Map<Long,Map<String,Object>> overrides=new HashMap<>();
        for(Map<String,Object> row:input)if(overrides.put(id(row.get("userId")),row)!=null)throw new ServiceException("人员不能重复");
        Map<Long,Map<String,Object>> facts=new HashMap<>();for(Map<String,Object> fact:(List<Map<String,Object>>)source.get("businessFacts"))facts.put(id(fact.get("factId")),fact);
        Set<Long> used=new HashSet<>();List<Map<String,Object>> rows=(List<Map<String,Object>>)source.get("rows");
        if(rows.size()!=overrides.size())throw new ServiceException("人员范围已变化，请重新获取人员成本");
        BigDecimal total=BigDecimal.ZERO,direct=BigDecimal.ZERO,business=BigDecimal.ZERO,pool=BigDecimal.ZERO;
        for(Map<String,Object> row:rows) {
            Map<String,Object> edit=overrides.get(id(row.get("userId")));if(edit==null)throw new ServiceException("人员范围已变化，请重新获取人员成本");
            if(!((List<?>)row.get("projectIssues")).isEmpty())throw new ServiceException(row.get("userName")+" 的项目人员成本待完善，请先核对后分摊");
            BigDecimal actual=amount(edit.get("totalAmount"));String reason=String.valueOf(edit.getOrDefault("reason","" )).trim();
            if(reason.length()>500)throw new ServiceException("人员成本说明不能超过500字");
            if((row.get("calculatedAmount")==null||actual.compareTo(decimal(row.get("calculatedAmount")))!=0)&&reason.isEmpty())throw new ServiceException(row.get("userName")+" 的月成本与系统测算不同，请填写核实依据");
            BigDecimal businessAmount=BigDecimal.ZERO;List<Long> links=new ArrayList<>();
            Object raw=edit.get("businessFactIds");if(raw instanceof List)for(Object value:(List<?>)raw) {
                Long factId=id(value);Map<String,Object> fact=facts.get(factId);
                if(fact==null||!used.add(factId))throw new ServiceException("关联的项目支出已冲销、重复或不属于本公司月份币种，请重新核对");
                businessAmount=businessAmount.add(decimal(fact.get("amount")));links.add(factId);
            }
            if(!links.isEmpty()&&reason.isEmpty())throw new ServiceException("关联外包等项目支出时，请填写人员与支出的对应依据");
            BigDecimal project=decimal(row.get("projectAmount"));BigDecimal remainder=remainder(actual,project,businessAmount);
            row.put("totalAmount",actual);row.put("businessAmount",businessAmount);row.put("businessFactIds",links);row.put("reason",reason);row.put("publicAmount",remainder);
            total=total.add(actual);direct=direct.add(project);business=business.add(businessAmount);pool=pool.add(remainder);
        }
        source.remove("businessFacts");source.put("totalAmount",total);source.put("projectAmount",direct);source.put("businessAmount",business);source.put("publicAmount",pool);
        source.put("dailyReference",pool.divide(new BigDecimal("21.75"),2,RoundingMode.HALF_UP));return source;
    }
    @SuppressWarnings("unchecked")
    public void validateSettlement(Map<String,Object> bill) {
        if(bill.get("personnelSnapshot")==null)return;
        Map<String,Object> saved=JSON.parseObject(String.valueOf(bill.get("personnelSnapshot")));
        if(Boolean.TRUE.equals(saved.get("estimated")))throw new ServiceException("公共人员成本尚为月内暂估，请退回并更新整月成本后重新下发");
        Map<String,Object> latest="AUTOMATIC".equals(saved.get("sourceMode"))?
            automaticSnapshot(id(bill.get("companyDeptId")),String.valueOf(bill.get("month")),String.valueOf(bill.get("currency")),saved):
            snapshot(id(bill.get("companyDeptId")),String.valueOf(bill.get("month")),String.valueOf(bill.get("currency")),(List<Map<String,Object>>)saved.get("rows"));
        // Diagnostic wording is not a financial source change; retain all monetary/source checks.
        stripDiagnostics(saved);stripDiagnostics(latest);
        if(!JSON.toJSONString(saved).equals(JSON.toJSONString(latest)))throw new ServiceException("人员成本或项目承担金额已变化，请退回更新公共人员成本并重新分配");
    }
    @SuppressWarnings("unchecked")
    private static void stripDiagnostics(Map<String,Object> snapshot) {
        for(Map<String,Object> row:(List<Map<String,Object>>)snapshot.get("rows"))
            for(String key:Arrays.asList("issues","projectIssues","issueDetails","projectIssueDetails"))row.remove(key);
    }
    private static String projectIssue(Map<String,Object> cost) {
        Object issue=cost.get("issue");
        if(issue==null&&cost.get("basisJson")!=null) {
            try { issue=JSON.parseObject(String.valueOf(cost.get("basisJson"))).get("issue"); }
            catch(RuntimeException ignored) { /* Keep an explicit unknown cause for historical records. */ }
        }
        return issue==null||String.valueOf(issue).trim().isEmpty()?"项目成本尚未核算，请核对该项目的人员成本明细":String.valueOf(issue);
    }
    @SuppressWarnings("unchecked")
    private static void addIssue(Set<String> summaries,List<Map<String,Object>> details,String reason,LocalDate date,Long projectId,String projectName) {
        if(summaries!=null)summaries.add(reason);
        Map<String,Object> detail=null;
        for(Map<String,Object> item:details)if(reason.equals(item.get("reason"))&&Objects.equals(projectId,item.get("projectId"))){detail=item;break;}
        if(detail==null){
            detail=new LinkedHashMap<>();detail.put("reason",reason);
            if(projectId!=null){detail.put("projectId",projectId);detail.put("projectName",projectName);}
            detail.put("dates",new ArrayList<String>());details.add(detail);
        }
        List<String> dates=(List<String>)detail.get("dates");
        if(date!=null&&!dates.contains(date.toString())){dates.add(date.toString());Collections.sort(dates);}
    }
    static BigDecimal remainder(BigDecimal total,BigDecimal project,BigDecimal business) {
        BigDecimal result=total.subtract(project).subtract(business);
        if(result.signum()<0)throw new ServiceException("项目已承担人员成本超过该人员月成本，请核对重复投入或关联支出，不能产生负的公共人员成本");return result;
    }
    private static BigDecimal amount(Object v){if(v==null)throw new ServiceException("请补齐人员月成本");BigDecimal n=decimal(v);if(n.signum()<0||n.scale()>2||n.precision()-n.scale()>16)throw new ServiceException("人员月成本须为非负金额，最多两位小数");return n.setScale(2);}
    private static BigDecimal decimal(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(v));}
    private static Long id(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
    private static LocalDate day(Object v){return v instanceof Date?LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",(Date)v)):LocalDate.parse(String.valueOf(v).substring(0,10));}
    private static boolean covers(Map<String,Object> row,LocalDate date){return (row.get("effectiveFrom")==null||!date.isBefore(day(row.get("effectiveFrom"))))&&(row.get("effectiveTo")==null||!date.isAfter(day(row.get("effectiveTo"))));}
}
