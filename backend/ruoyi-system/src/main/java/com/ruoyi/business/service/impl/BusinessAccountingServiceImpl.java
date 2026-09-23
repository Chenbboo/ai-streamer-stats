package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessIncentiveMapper;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

@Service
public class BusinessAccountingServiceImpl implements IBusinessAccountingService
{
    @org.springframework.beans.factory.annotation.Autowired
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    private java.time.Clock overviewClock = java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai"));
    void setOverviewClock(java.time.Clock clock) { overviewClock = clock.withZone(java.time.ZoneId.of("Asia/Shanghai")); }
    private static final List<String> MANUAL_EXPENSE_CATEGORY_CODES = Arrays.asList("PURCHASE_COST", "PLATFORM_FEE",
        "MARKETING_COST", "LOGISTICS_COST", "ADMIN_ALLOCATION", "OTHER_EXPENSE");
    @Autowired private BusinessAccountingMapper mapper;
    @Autowired private com.ruoyi.business.mapper.BusinessPublicExpenseMapper publicExpenses;
    @Autowired private BusinessProfitTaxService profitTax;
    @Autowired private BusinessIncentiveMapper incentiveMapper;
    @Autowired private BusinessFileService businessFileService;
    @Autowired private BusinessProjectWorkMapper workMapper;
    @Autowired private BusinessMemberDayCostService memberDays;
    @Autowired private com.ruoyi.business.mapper.BusinessFlowMapper flows;

    @Transactional
    public Map<String,Object> confirmNoSpend(Long projectId,Date date,Long actor,String name,boolean admin){
        Map<String,Object> project=requireContributorProject(projectId,actor,admin);
        ensureAccountingOpen(project);
        if(date==null||!day(date).equals(day(new Date())))throw new ServiceException("只能确认今日无支出");
        if(!Arrays.asList("ACTIVE","ACCEPTANCE").contains(project.get("status")))throw new ServiceException("只有执行中的项目可以确认今日无支出");
        if(project.get("companyDeptId")==null)throw new ServiceException("请先设置归属公司");
        if(!mapper.selectProjectDailySpendItems(projectId,date).isEmpty())throw new ServiceException("当日已有花费，请核对花费明细");
        mapper.confirmNoSpend(projectId,date,actor,name);
        return mapper.selectSpendConfirmation(projectId,date);
    }

    @Override
    public Map<String,Object> dashboard(Map<String,Object> query,Long userId,boolean viewAll)
    {
        Map<String,Object> scoped=scope(query,userId,viewAll);
        List<Map<String,Object>> dailyProjects=mapper.selectProjectOptions(userId,viewAll,true);
        if(dailyProjects!=null)for(Map<String,Object> p:dailyProjects)
            if(BusinessMemberDayCostService.POLICY.equals(p.get("costPolicyVersion"))
                &&(scoped.get("projectId")==null||String.valueOf(scoped.get("projectId")).equals(String.valueOf(p.get("projectId")))))
                memberDays.synchronize(longValue(p.get("projectId")));
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        Map<String,Object> summary=mapper.selectDailySummary(scoped);
        List<Map<String,Object>> summaryByCurrency=mapper.selectDailySummaryByCurrency(scoped);
        result.put("summary",summary);
        result.put("summaryByCurrency",summaryByCurrency);
        result.put("closedAdjustmentTotals",flows==null?Collections.emptyList():flows.adjustmentTotals(scoped));
        result.put("results",mapper.selectDailyResults(scoped));
        result.put("facts",mapper.selectFacts(scoped));
        result.put("companies",mapper.selectCompanies());
        List<Map<String,Object>> projects=mapper.selectProjectOptions(userId,viewAll,true);
        result.put("projects",projects);
        int pendingCosts=mapper.countPendingCostsInRange(scoped);
        result.put("pendingCostCount",pendingCosts);
        result.put("costDataStatus",pendingCosts>0?"PENDING_COST":"AVAILABLE");
        result.put("categories",mapper.selectCategories());
        addPublicExpenseReference(result,scoped);
        if(profitTax!=null)profitTax.decorate(result,scoped);
        return result;
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> bossOverview(Long userId,boolean viewAll)
    {
        // Existing AI tools explicitly ask for today; keep their contract.
        String currentDate=java.time.LocalDate.now(overviewClock).toString();
        return bossOverviewInternal(currentDate,currentDate,null,userId,viewAll,false);
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> bossOverview(String requestedDate,Long userId,boolean viewAll)
    { return bossOverview(requestedDate,null,userId,viewAll); }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> bossOverview(String requestedDate,Long companyDeptId,Long userId,boolean viewAll)
    {
        java.time.LocalDate today=java.time.LocalDate.now(overviewClock), date;
        if("yesterday".equals(requestedDate)) date=today.minusDays(1);
        else if(requestedDate==null||"today".equals(requestedDate)) date=today;
        else try {
            if(requestedDate==null||!requestedDate.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();
            date=java.time.LocalDate.parse(requestedDate);
        } catch(RuntimeException ex) { throw new ServiceException("经营日期须为有效的 yyyy-MM-dd 日期"); }
        if(date.isAfter(today))throw new ServiceException("经营日期不能晚于今天");
        return bossOverviewInternal(date.toString(),today.toString(),companyDeptId,userId,viewAll,true);
    }

    private Map<String,Object> bossOverviewInternal(String today,String currentDate,Long companyDeptId,Long userId,boolean viewAll,boolean review)
    {
        Date bizDate=java.sql.Date.valueOf(today);
        Map<String,Object> todayQuery=new HashMap<String,Object>();todayQuery.put("userId",userId);
        todayQuery.put("viewAll",viewAll);todayQuery.put("dateFrom",today);todayQuery.put("dateTo",today);
        todayQuery.put("companyDeptId",companyDeptId);
        Map<String,Object> alertQuery=new HashMap<String,Object>();alertQuery.put("userId",userId);
        alertQuery.put("viewAll",viewAll);alertQuery.put("bizDate",today);alertQuery.put("companyDeptId",companyDeptId);
        // 人员成本是否完整是公司级责任：即使员工尚未加入任何项目，也必须提醒对应公司老板设置。
        List<Map<String,Object>> personnelRows=mapper.selectCompanyPersonnelCostReadiness(userId,viewAll,java.sql.Date.valueOf(currentDate));
        if(companyDeptId!=null)personnelRows=personnelRows.stream()
            .filter(row->companyDeptId.equals(longValue(row.get("companyDeptId")))).collect(java.util.stream.Collectors.toList());
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("bizDate",today);
        result.put("currentBizDate",currentDate);
        if(review)
        {
            Map<String,Map<String,Object>> companyOptions=new LinkedHashMap<>();
            List<Map<String,Object>> accessibleCompanies=viewAll?mapper.selectCompanies():profitTax==null?Collections.emptyList():profitTax.settings(userId);
            for(Map<String,Object> company:accessibleCompanies)
                companyOptions.put(String.valueOf(company.get("companyDeptId")),company);
            for(Map<String,Object> project:mapper.selectProjectOptions(userId,viewAll,true))
                if(project.get("companyDeptId")!=null)
                    companyOptions.putIfAbsent(String.valueOf(project.get("companyDeptId")),project);
            result.put("companyOptions",new ArrayList<>(companyOptions.values()));
        }
        result.put("companyDeptId",companyDeptId);
        result.put("missingDailyResultCount",mapper.countProjectsMissingDailyResult(userId,viewAll,bizDate,companyDeptId));
        result.put("today",mapper.selectDailySummary(todayQuery));
        result.put("todayByCurrency",mapper.selectDailySummaryByCurrency(todayQuery));
        result.put("draftFactCount",mapper.countDraftFacts(todayQuery));
        if(review)alertQuery.put("alertScope","PERIOD");
        result.put("alerts",mapper.selectAccountingAlerts(alertQuery));
        result.put("personnelReadiness",summarizePersonnelReadiness(personnelRows));
        result.put("ranking",mapper.selectProjectProfitRanking(todayQuery));
        result.put("companies",mapper.selectCompanyAccountingSummary(todayQuery));
        addPublicExpenseReference(result,todayQuery);
        if(review)
        {
            Map<String,Object> readiness=mapper.selectOverviewReadiness(todayQuery);
            if(readiness==null)readiness=Collections.emptyMap();
            result.put("readiness",readiness);
            result.put("summary",result.get("today"));
            result.put("summaryByCurrency",result.get("todayByCurrency"));
            int missing=((Number)result.get("missingDailyResultCount")).intValue();
            boolean incomplete=missing>0||decimal(readiness.get("pendingCostCount")).signum()>0
                ||decimal(readiness.get("unfinishedFactCount")).signum()>0||decimal(readiness.get("unfinishedWorkCount")).signum()>0;
            result.put("dataStatus",incomplete?"INCOMPLETE":decimal(readiness.get("resultCount")).signum()==0?"NO_DATA":"AVAILABLE");
            alertQuery.put("alertScope","CURRENT");alertQuery.put("bizDate",currentDate);
            result.put("currentAlerts",mapper.selectAccountingAlerts(alertQuery));
        }
        if(profitTax!=null)profitTax.decorate(result,todayQuery);
        return result;
    }

    /** Public daily costs are already included in the operating result; never deduct them again. */
    @SuppressWarnings("unchecked")
    private void addPublicExpenseReference(Map<String,Object> result,Map<String,Object> query)
    {
        Object from=query.get("dateFrom"),to=query.get("dateTo");
        if(from==null||!from.equals(to)||!String.valueOf(from).matches("\\d{4}-\\d{2}-\\d{2}"))return;
        List<Map<String,Object>> rows=mapper.selectPublicExpenseReferences(query);
        if(rows==null)rows=Collections.emptyList();
        Map<String,Map<String,Object>> currencies=new TreeMap<>();
        int pending=0;
        for(Map<String,Object> row:rows)
        {
            row.put("dailyReference",decimal(row.get("recognizedPublicCost")));
            Map<String,Object> daily=publicExpenses==null?null:publicExpenses.sumDailyCost(longValue(row.get("projectId")),DateUtils.parseDate(String.valueOf(from)));
            row.put("estimatedAmount",daily==null?BigDecimal.ZERO:decimal(daily.get("estimatedAmount")));
            String currency=String.valueOf(row.get("currency"));
            Map<String,Object> total=currencies.get(currency);
            if(total==null){total=new LinkedHashMap<>();total.put("currency",currency);total.put("dailyReference",BigDecimal.ZERO);
                total.put("pendingCount",0);total.put("missingResultCount",0);currencies.put(currency,total);}
            total.put("dailyReference",decimal(total.get("dailyReference")).add(decimal(row.get("dailyReference"))));
            int count=decimal(row.get("pendingCount")).intValue();pending+=count;
            total.put("pendingCount",((Integer)total.get("pendingCount"))+count);
            if(row.get("profitAmount")==null)total.put("missingResultCount",((Integer)total.get("missingResultCount"))+1);
            row.put("referenceProfit",count>0||row.get("profitAmount")==null?null:
                decimal(row.get("profitAmount")));
        }
        List<Map<String,Object>> summaries=(List<Map<String,Object>>)result.get("summaryByCurrency");
        if(summaries==null)summaries=(List<Map<String,Object>>)result.get("todayByCurrency");
        if(summaries!=null)for(Map<String,Object> summary:summaries)
        {
            Map<String,Object> total=currencies.get(String.valueOf(summary.get("currency")));
            if(total!=null&&decimal(total.get("pendingCount")).signum()==0&&decimal(total.get("missingResultCount")).signum()==0
                &&summary.get("profitAmount")!=null)
                total.put("referenceProfit",decimal(summary.get("profitAmount")));
        }
        Map<String,Object> reference=new LinkedHashMap<>();reference.put("month",String.valueOf(from).substring(0,7));
        reference.put("recognitionMode","DAILY_V1");reference.put("rows",rows);
        reference.put("byCurrency",new ArrayList<>(currencies.values()));reference.put("pendingCount",pending);
        result.put("publicExpenseReference",reference);
    }

    @Override
    public Map<String,Object> personnelCostOverview(Map<String,Object> query,Long userId,boolean viewAll)
    {
        Map<String,Object> scoped=scope(query,userId,viewAll);
        String bizDate=String.valueOf(scoped.get("bizDate"));
        if(StringUtils.isBlank(bizDate)||"null".equals(bizDate))
            bizDate=new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        scoped.put("bizDate",bizDate);
        List<Map<String,Object>> oldRows=mapper.selectPersonnelCostOverview(scoped);
        List<Map<String,Object>> rows=new java.util.ArrayList<Map<String,Object>>();if(oldRows!=null)rows.addAll(oldRows);
        List<Map<String,Object>> workRows=workMapper.selectPersonnelCostOverview(scoped);if(workRows!=null)rows.addAll(workRows);
        for(Map<String,Object> p:mapper.selectProjectOptions(userId,viewAll,true))
            if(BusinessMemberDayCostService.POLICY.equals(p.get("costPolicyVersion"))
                &&(scoped.get("projectId")==null||longValue(scoped.get("projectId")).equals(longValue(p.get("projectId"))))
                &&(scoped.get("companyDeptId")==null||longValue(scoped.get("companyDeptId")).equals(longValue(p.get("companyDeptId")))))
                rows.addAll(memberDays.overview(longValue(p.get("projectId")),bizDate));
        boolean rawCostVisible=viewAll;
        if(!rawCostVisible)try{rawCostVisible=com.ruoyi.common.utils.SecurityUtils.hasPermi("business:staff:cost");}catch(ServiceException ex){rawCostVisible=false;}
        for(Map<String,Object> row:rows){row.put("rawCostVisible",rawCostVisible);if(!rawCostVisible)for(String field:Arrays.asList("monthlyCost","dailyCost","standardWorkDays","policyId","basisJson","calculationDetail"))row.remove(field);}
        int readyCount=0,issueCount=0,overAllocatedCount=0;
        BigDecimal personnelCost=BigDecimal.ZERO;
        if(rows!=null)for(Map<String,Object> row:rows)
        {
            String status=String.valueOf(row.get("costStatus"));
            if("READY".equals(status)||"LEAVE".equals(status))readyCount++;else issueCount++;
            if("OVER_ALLOCATED".equals(status))overAllocatedCount++;
            personnelCost=personnelCost.add(decimal(row.get("personnelCost")));
        }
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("bizDate",bizDate);result.put("rows",rows);
        result.put("readyCount",readyCount);result.put("issueCount",issueCount);
        result.put("overAllocatedCount",overAllocatedCount);result.put("personnelCost",personnelCost);result.put("rawCostVisible",rawCostVisible);result.put("hasUnpricedOrMissingWork",rows.stream().anyMatch(row -> Arrays.asList("MISSING_ACTUAL","PENDING_COST").contains(row.get("costStatus"))));
        Map<String,BigDecimal> currencyTotals=new TreeMap<>();
        for(Map<String,Object> row:rows)currencyTotals.merge(String.valueOf(row.getOrDefault("currency","UNKNOWN")),decimal(row.get("personnelCost")),BigDecimal::add);
        List<Map<String,Object>> totals=new ArrayList<>();for(Map.Entry<String,BigDecimal> e:currencyTotals.entrySet()){Map<String,Object> t=new LinkedHashMap<>();t.put("currency",e.getKey());t.put("amount",e.getValue());totals.add(t);}
        result.put("amountsByCurrency",totals);if(currencyTotals.size()>1)result.put("personnelCost",null);
        result.put("readiness",summarizePersonnelReadiness(rows));
        result.put("companyUniquePersonnelTotals",com.ruoyi.business.support.BusinessPersonnelTotals.summarize(rows));
        return result;
    }

    private Map<String,Object> summarizePersonnelReadiness(List<Map<String,Object>> rows)
    {
        Map<Long,Map<String,Object>> issueByUser=new LinkedHashMap<Long,Map<String,Object>>();
        if(rows!=null)for(Map<String,Object> row:rows)
        {
            String status=String.valueOf(row.get("costStatus"));
            if(!Arrays.asList("MISSING_REGION","MISSING_COST","LEGACY_COST").contains(status))continue;
            Long staffUserId=longValue(row.get("userId"));
            Map<String,Object> issue=issueByUser.get(staffUserId);
            if(issue==null)
            {
                issue=new LinkedHashMap<String,Object>();
                issue.put("userId",staffUserId);issue.put("userName",row.get("userName"));
                issue.put("companyDeptId",row.get("companyDeptId"));issue.put("companyName",row.get("companyName"));
                issue.put("countryRegion",row.get("profileCountryRegion"));issue.put("costStatus",status);
                issue.put("queriedProjectCount",row.get("projectCount"));
                issue.put("projectIds",new java.util.ArrayList<Long>());
                issue.put("projectNames",new java.util.ArrayList<String>());
                issueByUser.put(staffUserId,issue);
            }
            if("MISSING_REGION".equals(status)||("LEGACY_COST".equals(status)&&"MISSING_COST".equals(issue.get("costStatus"))))
                issue.put("costStatus",status);
            @SuppressWarnings("unchecked") List<Long> projectIds=(List<Long>)issue.get("projectIds");
            Long projectId=longValue(row.get("projectId"));
            if(projectId!=null&&!projectIds.contains(projectId))
            {
                projectIds.add(projectId);
                @SuppressWarnings("unchecked") List<String> projectNames=(List<String>)issue.get("projectNames");
                projectNames.add(String.valueOf(row.get("projectName")));
            }
            String projectNameText=row.get("projectNameText")==null?null:String.valueOf(row.get("projectNameText"));
            if(StringUtils.isNotBlank(projectNameText))
            {
                @SuppressWarnings("unchecked") List<String> projectNames=(List<String>)issue.get("projectNames");
                for(String name:projectNameText.split("、"))
                    if(StringUtils.isNotBlank(name)&&!projectNames.contains(name))projectNames.add(name);
            }
        }
        int missingRegionCount=0,missingCostCount=0,legacyCostCount=0;
        List<Map<String,Object>> issues=new java.util.ArrayList<Map<String,Object>>(issueByUser.values());
        for(Map<String,Object> issue:issues)
        {
            String status=String.valueOf(issue.get("costStatus"));
            if("MISSING_REGION".equals(status))missingRegionCount++;
            else if("LEGACY_COST".equals(status))legacyCostCount++;
            else missingCostCount++;
            @SuppressWarnings("unchecked") List<Long> projectIds=(List<Long>)issue.get("projectIds");
            Object queriedCount=issue.remove("queriedProjectCount");
            issue.put("projectCount",queriedCount==null?projectIds.size():Integer.valueOf(String.valueOf(queriedCount)));
            issue.remove("projectIds");
        }
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("issueCount",issues.size());result.put("missingRegionCount",missingRegionCount);
        result.put("missingCostCount",missingCostCount);result.put("legacyCostCount",legacyCostCount);
        result.put("issues",issues);return result;
    }

    @Override public List<Map<String,Object>> facts(Map<String,Object> query,Long userId,boolean viewAll)
    { return mapper.selectFacts(scope(query,userId,viewAll)); }

    @Override
    @Transactional
    public BusinessOperatingFact saveFact(BusinessOperatingFact fact,Long userId,String userName,boolean viewAll)
    { return saveFactInternal(fact,userId,userName,viewAll,false); }

    @Override
    @Transactional
    public BusinessOperatingFact saveProjectFact(BusinessOperatingFact fact,Long userId,String userName,boolean viewAll)
    { return saveFactInternal(fact,userId,userName,viewAll,true); }

    @Override
    @Transactional
    public BusinessOperatingFact saveProjectDailySpend(BusinessOperatingFact fact,Long userId,String userName,boolean viewAll)
    {
        if(fact==null||fact.getProjectId()==null)throw new ServiceException("请选择归属项目");
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(fact.getProjectId());
        if(project==null)throw new ServiceException("项目不存在");
        if(!viewAll&&!String.valueOf(userId).equals(String.valueOf(project.get("mainOwnerUserId"))))
            throw new ServiceException("只有项目主负责人可以填写今日项目总花费");
        if(fact.getBizDate()==null)fact.setBizDate(new Date());
        String requestKey=submissionKey(fact,userId,"SPEND");
        BusinessOperatingFact replay=replaySubmission(fact,requestKey);
        if(replay!=null)return replay;
        ensureAccountingOpen(project);
        boolean lateSettlement=isPostDeliverySettlement(project);
        if(!lateSettlement&&!Arrays.asList("ACTIVE","ACCEPTANCE").contains(String.valueOf(project.get("status"))))
            throw new ServiceException("项目进入执行中后才能填写今日项目总花费");
        if(project.get("companyDeptId")==null)throw new ServiceException("该项目尚未设置归属公司");
        if(fact.getBizDate()==null)fact.setBizDate(new Date());
        ensureBusinessDate(project,fact.getBizDate(),false);
        String bizDate=new SimpleDateFormat("yyyy-MM-dd").format(fact.getBizDate());
        String today=new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if(!lateSettlement&&!today.equals(bizDate))throw new ServiceException("执行中的项目只能填写今日项目总花费");
        if(fact.getAmount()==null||fact.getAmount().compareTo(BigDecimal.ZERO)<=0)
            throw new ServiceException("本次花费必须大于0");
        if(StringUtils.isBlank(fact.getDescription()))throw new ServiceException("请填写本次花费用途");
        businessFileService.validateReferences(fact.getAttachmentUrls(), fact.getProjectId(), userId, false, viewAll);
        Map<String,Object> category=fact.getCategoryId()==null
            ?mapper.selectCategoryByCode("OTHER_EXPENSE")
            :mapper.selectCategoryById(fact.getCategoryId());
        if(category==null||!MANUAL_EXPENSE_CATEGORY_CODES.contains(String.valueOf(category.get("categoryCode")))
            ||(category.get("factKind")!=null&&!"COST".equals(String.valueOf(category.get("factKind")))))
            throw new ServiceException("请选择有效的支出类别");
        if(StringUtils.isBlank(fact.getCurrency()))fact.setCurrency(String.valueOf(project.get("currency")));
        fact.setCurrency(fact.getCurrency().trim().toUpperCase());
        if(!fact.getCurrency().matches("^[A-Z]{3}$"))throw new ServiceException("币种必须是 ISO 4217 的3位大写英文代码");
        if(!fact.getCurrency().equals(String.valueOf(project.get("currency")).toUpperCase()))
            throw new ServiceException("支出币种必须与项目本位币一致，当前为 "+project.get("currency"));

        BusinessOperatingFact previous=null;
        if(fact.getFactId()!=null)
        {
            previous=mapper.selectFactByIdForUpdate(fact.getFactId());
            if(previous==null||!fact.getProjectId().equals(previous.getProjectId())
                ||previous.getBizDate()==null||!day(fact.getBizDate()).equals(day(previous.getBizDate()))
                ||!"PROJECT_DAILY".equals(previous.getSourceDomain())
                ||!Arrays.asList("DAILY_TOTAL","DAILY_ITEM").contains(previous.getSourceType()))
                throw new ServiceException("只能修改本项目所选日期的花费明细");
            if(!"CONFIRMED".equals(previous.getStatus()))throw new ServiceException("只有已计入的花费可以修改");
        }
        fact.setCompanyDeptId(longValue(project.get("companyDeptId")));
        fact.setCategoryId(longValue(category.get("categoryId")));
        fact.setCategoryCode(String.valueOf(category.get("categoryCode")));
        fact.setCategoryName(String.valueOf(category.get("categoryName")));fact.setFactKind("COST");
        fact.setDescription(fact.getDescription().trim());
        fact.setSourceDomain("PROJECT_DAILY");fact.setSourceType("DAILY_ITEM");fact.setSourceId(IdUtils.fastSimpleUUID());
        fact.setSourceLineKey("ITEM");
        fact.setStatus("DRAFT");
        if(previous!=null)createReversal(previous,"负责人修改项目花费明细",userId,userName);
        fact.setFactId(null);fact.setIdempotencyKey(requestKey);
        fact.setCreateUserId(userId);fact.setCreateBy(userName);mapper.insertFact(fact);
        BusinessOperatingFact draft=mapper.selectFactById(fact.getFactId());
        if(mapper.confirmFact(draft.getFactId(),userId,userName,draft.getVersion())!=1)throw changed();
        recalculateInternal(fact.getProjectId(),fact.getBizDate(),userName);
        return mapper.selectFactById(fact.getFactId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessOperatingFact reverseProjectDailySpend(Long factId,String reason,Long userId,String userName,boolean viewAll)
    {
        if(StringUtils.isBlank(reason))throw new ServiceException("请填写冲销原因");
        BusinessOperatingFact original=mapper.selectFactByIdForUpdate(factId);
        if(original==null)throw new ServiceException("花费明细不存在");
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(original.getProjectId());
        if(project==null)throw new ServiceException("项目不存在");
        if(!viewAll&&!String.valueOf(userId).equals(String.valueOf(project.get("mainOwnerUserId"))))
            throw new ServiceException("只有项目主负责人可以冲销项目花费");
        ensureAccountingOpen(project);
        if(!"PROJECT_DAILY".equals(original.getSourceDomain())
            ||!Arrays.asList("DAILY_TOTAL","DAILY_ITEM").contains(original.getSourceType()))
            throw new ServiceException("只能冲销项目花费明细");
        if(!"CONFIRMED".equals(original.getStatus()))throw new ServiceException("只有已计入的花费可以冲销");
        BusinessOperatingFact reversal=createReversal(original,reason.trim(),userId,userName);
        recalculateInternal(original.getProjectId(),original.getBizDate(),userName);
        return reversal;
    }

    @Override
    @Transactional
    public void recordSubprojectFundingTransfer(Long parentProjectId,Long childProjectId,Long proposalId,
        BigDecimal amount,String currency,String reason,Long userId,String userName)
    {
        if(parentProjectId==null||childProjectId==null||proposalId==null)
            throw new ServiceException("子项目拨款关联信息不完整");
        if(parentProjectId.equals(childProjectId))throw new ServiceException("主项目和子项目不能相同");
        if(amount==null||amount.signum()<=0)throw new ServiceException("子项目拨款金额必须大于0");
        Map<String,Object> parent=mapper.selectProjectForAccountingForUpdate(parentProjectId);
        Map<String,Object> child=mapper.selectProjectForAccountingForUpdate(childProjectId);
        if(parent==null||child==null)throw new ServiceException("拨款关联的主项目或子项目不存在");
        ensureAccountingOpen(parent);ensureAccountingOpen(child);
        if(!java.util.Objects.equals(longValue(parent.get("companyDeptId")),longValue(child.get("companyDeptId"))))
            throw new ServiceException("主项目和子项目必须归属同一公司");
        String transferCurrency=StringUtils.defaultIfBlank(currency,String.valueOf(parent.get("currency"))).trim().toUpperCase();
        if(!transferCurrency.equals(String.valueOf(parent.get("currency")))
            ||!transferCurrency.equals(String.valueOf(child.get("currency"))))
            throw new ServiceException("主项目、子项目与拨款币种必须一致");
        Date bizDate=day(new Date());
        String parentKey="SUBPROJECT_FUNDING:"+proposalId+":PARENT";
        String childKey="SUBPROJECT_FUNDING:"+proposalId+":CHILD";
        BusinessOperatingFact existingParent=mapper.selectFactByIdempotencyKey(parentKey);
        BusinessOperatingFact existingChild=mapper.selectFactByIdempotencyKey(childKey);
        if(existingParent==null)insertFundingFact(parent,child,proposalId,amount,transferCurrency,reason,userId,userName,
            "SUBPROJECT_FUNDING_COST","子项目拨款支出","COST","PARENT_COST",parentKey,bizDate);
        if(existingChild==null)insertFundingFact(child,parent,proposalId,amount,transferCurrency,reason,userId,userName,
            "SUBPROJECT_FUNDING_REVENUE","主项目拨款收入","REVENUE","CHILD_REVENUE",childKey,bizDate);
        if(existingParent==null)recalculateInternal(parentProjectId,bizDate,userName);
        if(existingChild==null)recalculateInternal(childProjectId,bizDate,userName);
    }

    private void insertFundingFact(Map<String,Object> project,Map<String,Object> counterparty,Long proposalId,
        BigDecimal amount,String currency,String reason,Long userId,String userName,String categoryCode,
        String categoryName,String factKind,String sourceType,String idempotencyKey,Date bizDate)
    {
        Map<String,Object> category=mapper.selectCategoryByCode(categoryCode);
        if(category==null)throw new ServiceException("子项目拨款核算类别未启用，请先执行数据库升级");
        BusinessOperatingFact fact=new BusinessOperatingFact();
        fact.setProjectId(longValue(project.get("projectId")));fact.setCompanyDeptId(longValue(project.get("companyDeptId")));
        fact.setBizDate(bizDate);fact.setCategoryId(longValue(category.get("categoryId")));
        fact.setCategoryCode(categoryCode);fact.setCategoryName(categoryName);fact.setFactKind(factKind);
        fact.setAmount(amount.setScale(2,java.math.RoundingMode.HALF_UP));fact.setCurrency(currency);
        fact.setDescription(categoryName+"："+StringUtils.defaultIfBlank(reason,"主子项目内部转拨"));
        fact.setCounterparty(String.valueOf(counterparty.get("projectName")));
        fact.setSourceDomain("SUBPROJECT_FUNDING");fact.setSourceType(sourceType);
        fact.setSourceId(String.valueOf(proposalId));fact.setSourceLineKey(String.valueOf(counterparty.get("projectId")));
        fact.setStatus("CONFIRMED");fact.setIdempotencyKey(idempotencyKey);
        fact.setConfirmedUserId(userId);fact.setConfirmedUserName(userName);fact.setConfirmedTime(new Date());
        fact.setCreateUserId(userId);fact.setCreateBy(userName);fact.setRemark("内部转拨，公司合并报表抵销");
        mapper.insertFact(fact);
    }

    private BusinessOperatingFact saveFactInternal(BusinessOperatingFact fact,Long userId,String userName,
        boolean viewAll,boolean projectContributor)
    {
        if(fact==null||fact.getProjectId()==null)throw new ServiceException("请选择归属项目");
        Map<String,Object> project=projectContributor
            ?requireContributorProject(fact.getProjectId(),userId,viewAll)
            :requireProject(fact.getProjectId(),userId,viewAll);
        String requestKey=submissionKey(fact,userId,projectContributor?"PROJECT":"MANUAL");
        if(fact.getFactId()==null){BusinessOperatingFact replay=replaySubmission(fact,requestKey);if(replay!=null)return replay;}
        ensureAccountingOpen(project);
        if(project.get("companyDeptId")==null)throw new ServiceException("该项目尚未设置归属公司，请先编辑项目选择上海或越南公司");
        Map<String,Object> category=mapper.selectCategoryById(fact.getCategoryId());
        if(category==null)throw new ServiceException("请选择有效的收支类别");
        if("COMPANY_PUBLIC_COST".equals(String.valueOf(category.get("categoryCode"))))
            throw new ServiceException("公司公共费用只能通过月结或公共费用调整入账，不能手工录入");
        if("PROJECT_MANAGEMENT_FEE".equals(String.valueOf(category.get("categoryCode"))))
            throw new ServiceException("项目管理费只能在关闭项目核算时由系统确认，不能手工录入");
        if(String.valueOf(category.get("categoryCode")).startsWith("SUBPROJECT_FUNDING_"))
            throw new ServiceException("子项目拨款收支由项目启动流程自动入账，不能手工录入");
        if(fact.getBizDate()==null)throw new ServiceException("请选择业务日期");
        ensureBusinessDate(project,fact.getBizDate(),false);
        if(StringUtils.isBlank(fact.getDescription()))throw new ServiceException("请填写收支说明");
        businessFileService.validateReferences(fact.getAttachmentUrls(), fact.getProjectId(), userId, false, viewAll);
        String kind=String.valueOf(category.get("factKind"));
        if(projectContributor)
        {
            boolean lateSettlement=isPostDeliverySettlement(project);
            if(!lateSettlement&&!Arrays.asList("ACTIVE","ACCEPTANCE").contains(String.valueOf(project.get("status"))))
                throw new ServiceException("项目进入执行中后才能提交今日填报");
            String bizDate=new SimpleDateFormat("yyyy-MM-dd").format(fact.getBizDate());
            String today=new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if(!lateSettlement&&!today.equals(bizDate))throw new ServiceException("执行中的项目只能提交今日数据");
            if(!Arrays.asList("REVENUE","VALUE").contains(kind))
                throw new ServiceException("负责人可在项目工作台填报收入、成果和今日总花费；核算调整仍由财务处理");
        }
        if("VALUE".equals(kind))
        {
            if(fact.getQuantity()==null)throw new ServiceException("请填写成果数值");
            fact.setAmount(null);
        }
        else
        {
            if(fact.getAmount()==null)throw new ServiceException("请填写金额");
            if(!"ADJUSTMENT".equals(kind)&&fact.getAmount().compareTo(BigDecimal.ZERO)<0)
                throw new ServiceException("收入和成本金额不能为负数，红冲请使用冲销功能");
            if(StringUtils.isBlank(fact.getCurrency()))fact.setCurrency(String.valueOf(project.get("currency")));
            fact.setCurrency(fact.getCurrency().trim().toUpperCase());
            if(!fact.getCurrency().matches("^[A-Z]{3}$"))throw new ServiceException("币种必须是 ISO 4217 的3位大写英文代码");
            if(!fact.getCurrency().equals(String.valueOf(project.get("currency")).toUpperCase()))
                throw new ServiceException("收支币种必须与项目本位币一致，当前为 "+project.get("currency"));
        }
        fact.setCompanyDeptId(longValue(project.get("companyDeptId")));
        fact.setCategoryCode(String.valueOf(category.get("categoryCode")));
        fact.setCategoryName(String.valueOf(category.get("categoryName")));
        fact.setFactKind(kind);
        fact.setSourceDomain("MANUAL");fact.setSourceType("MANUAL");
        if(fact.getFactId()==null)
        {
            fact.setStatus("DRAFT");fact.setIdempotencyKey(requestKey);
            fact.setCreateUserId(userId);fact.setCreateBy(userName);mapper.insertFact(fact);
        }
        else
        {
            BusinessOperatingFact current=projectContributor
                ?requireContributorFact(fact.getFactId(),userId,viewAll)
                :requireFact(fact.getFactId(),userId,viewAll);
            if ("HR_INCENTIVE".equals(current.getSourceDomain()))
                throw new ServiceException("奖励核准产生的成本不能在普通收支中修改，请回到奖金激励办理");
            if(!Arrays.asList("DRAFT","RETURNED").contains(current.getStatus()))
                throw new ServiceException("只有草稿或已退回的收支可以修改");
            if(!current.getProjectId().equals(fact.getProjectId()))throw new ServiceException("草稿不能更换归属项目");
            fact.setVersion(current.getVersion());fact.setUpdateBy(userName);
            if(mapper.updateDraftFact(fact)!=1)throw changed();
        }
        BusinessOperatingFact stored=mapper.selectFactById(fact.getFactId());
        // Both accounting and project entry post atomically with the daily result.
        if(mapper.confirmFact(stored.getFactId(),userId,userName,stored.getVersion())!=1)throw changed();
        recalculateInternal(stored.getProjectId(),stored.getBizDate(),userName);
        return mapper.selectFactById(stored.getFactId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessOperatingFact confirmFact(Long factId,Long userId,String userName,boolean viewAll)
    {
        BusinessOperatingFact fact=requireFact(factId,userId,viewAll);
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(fact.getProjectId());
        ensureAccountingOpen(project);
        requireAwardCostAuthority(fact, project, userId);
        if ("HR_INCENTIVE".equals(fact.getSourceDomain()) && "CONFIRMED".equals(fact.getStatus())) return fact;
        ensureBusinessDate(project,fact.getBizDate(),false);
        if(!"DRAFT".equals(fact.getStatus()))throw new ServiceException("只有草稿可以确认入账");
        if("DAILY_TOTAL".equals(fact.getSourceType()))
        {
            BusinessOperatingFact previous=mapper.selectConfirmedProjectDailySpend(fact.getProjectId(),fact.getBizDate());
            if(previous!=null&&!previous.getFactId().equals(fact.getFactId()))
                createReversal(previous,"确认修改后的今日项目总花费",userId,userName);
        }
        if(mapper.confirmFact(factId,userId,userName,fact.getVersion())!=1)throw changed();
        recalculateInternal(fact.getProjectId(),fact.getBizDate(),userName);
        return mapper.selectFactById(factId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessOperatingFact returnFact(Long factId,String reason,Long userId,String userName,boolean viewAll)
    {
        if(StringUtils.isBlank(reason))throw new ServiceException("请填写退回原因");
        BusinessOperatingFact fact=requireFact(factId,userId,viewAll);
        Map<String,Object> project = mapper.selectProjectForAccountingForUpdate(fact.getProjectId());
        ensureAccountingOpen(project);
        requireAwardCostAuthority(fact, project, userId);
        if(!"DRAFT".equals(fact.getStatus()))throw new ServiceException("只有待确认草稿可以退回");
        String normalizedReason=reason.trim();
        if(normalizedReason.length()>500)throw new ServiceException("退回原因不能超过500个字");
        if(mapper.returnFact(factId,normalizedReason,userId,userName,fact.getVersion())!=1)throw changed();
        return mapper.selectFactById(factId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusinessOperatingFact reverseFact(Long factId,String reason,Long userId,String userName,boolean viewAll)
    {
        if(StringUtils.isBlank(reason))throw new ServiceException("请填写冲销原因");
        BusinessOperatingFact original=requireFact(factId,userId,viewAll);
        if("COMPANY_PUBLIC_COST".equals(original.getCategoryCode()))
            throw new ServiceException("公司公共费用须在原月账中登记调整，不能单独冲销分摊流水");
        Map<String,Object> project = mapper.selectProjectForAccountingForUpdate(original.getProjectId());
        ensureAccountingOpen(project);
        requireAwardCostAuthority(original, project, userId);
        if(!"CONFIRMED".equals(original.getStatus()))throw new ServiceException("只有已确认流水可以冲销");
        BusinessOperatingFact reversal=createReversal(original,reason,userId,userName);
        recalculateInternal(original.getProjectId(),original.getBizDate(),userName);
        return reversal;
    }

    private String submissionKey(BusinessOperatingFact fact,Long actor,String kind)
    {
        String request=fact.getRequestId();
        // Old clients remain supported; updated forms keep this ID throughout a retry.
        if(StringUtils.isBlank(request))request=IdUtils.fastSimpleUUID();
        if(!request.matches("[A-Za-z0-9_-]{16,64}"))throw new ServiceException("提交编号无效，请重新打开表单");
        return kind+"-"+fact.getProjectId()+"-"+actor+"-"+request;
    }
    private BusinessOperatingFact replaySubmission(BusinessOperatingFact input,String key)
    {
        BusinessOperatingFact saved=mapper.selectFactByIdempotencyKey(key);
        if(saved==null)return null;
        if(!java.util.Objects.equals(saved.getProjectId(),input.getProjectId())
            ||!sameDay(saved.getBizDate(),input.getBizDate())
            ||decimal(saved.getAmount()).compareTo(decimal(input.getAmount()))!=0
            ||decimal(saved.getQuantity()).compareTo(decimal(input.getQuantity()))!=0
            ||(!key.startsWith("SPEND-")&&!java.util.Objects.equals(saved.getCategoryId(),input.getCategoryId()))
            ||(!StringUtils.isBlank(input.getCurrency())&&!input.getCurrency().trim().equalsIgnoreCase(saved.getCurrency()))
            ||!StringUtils.defaultString(saved.getAttachmentUrls()).equals(StringUtils.defaultString(input.getAttachmentUrls()))
            ||!StringUtils.defaultString(saved.getCounterparty()).equals(StringUtils.defaultString(input.getCounterparty()))
            ||!java.util.Objects.equals(StringUtils.trim(saved.getDescription()),StringUtils.trim(input.getDescription())))
            throw new ServiceException("本次提交已保存，内容发生变化，请刷新并使用修改或新增入口");
        return saved;
    }

    private void requireAwardCostAuthority(BusinessOperatingFact fact, Map<String,Object> project, Long userId)
    {
        if (!"HR_INCENTIVE".equals(fact.getSourceDomain())) return;
        if (userId == null || !String.valueOf(userId).equals(String.valueOf(project.get("initiatorUserId"))))
            throw new ServiceException("奖励成本须由项目归属核算责任人处理，技术管理员不能代替业务签认");
        Long awardId;
        try { awardId = Long.valueOf(fact.getSourceId()); }
        catch (RuntimeException ex) { throw new ServiceException("奖励成本来源异常，请核对原奖励单"); }
        BusinessIncentiveAward award = incentiveMapper.selectAwardForUpdate(awardId);
        if (award == null || !"APPROVED".equals(award.getStatus())
            || !fact.getProjectId().equals(award.getProjectId())
            || !fact.getFactId().equals(award.getAccountingFactId())
            || !java.util.Objects.equals(fact.getCompanyDeptId(), award.getCompanyDeptId())
            || !java.util.Objects.equals(fact.getCompanyDeptId(), longValue(project.get("companyDeptId")))
            || !"BONUS".equals(fact.getSourceType())
            || !"AWARD".equals(fact.getSourceLineKey())
            || !"COST".equals(fact.getFactKind())
            || !"PROJECT_BONUS_COST".equals(fact.getCategoryCode())
            || !("HR-INCENTIVE-AWARD-" + awardId).equals(fact.getIdempotencyKey())
            || fact.getBizDate() == null || award.getBizDate() == null
            || !day(fact.getBizDate()).equals(day(award.getBizDate()))
            || !String.valueOf(fact.getCurrency()).equals(award.getCurrency())
            || fact.getAmount() == null || award.getAmount() == null || fact.getAmount().compareTo(award.getAmount()) != 0)
            throw new ServiceException("奖励成本必须匹配有效核准单，金额和来源不能被普通收支覆盖");
    }

    private BusinessOperatingFact createReversal(BusinessOperatingFact original,String reason,Long userId,String userName)
    {
        if(mapper.markFactReversed(original.getFactId(),userName,original.getVersion())!=1)throw changed();
        BusinessOperatingFact reversal=new BusinessOperatingFact();
        reversal.setProjectId(original.getProjectId());reversal.setCompanyDeptId(original.getCompanyDeptId());
        reversal.setBizDate(original.getBizDate());reversal.setCategoryId(original.getCategoryId());
        reversal.setCategoryCode(original.getCategoryCode());reversal.setCategoryName(original.getCategoryName());
        reversal.setFactKind(original.getFactKind());reversal.setAmount(negate(original.getAmount()));
        reversal.setQuantity(negate(original.getQuantity()));reversal.setCurrency(original.getCurrency());reversal.setUnit(original.getUnit());
        reversal.setDescription("冲销："+original.getDescription());reversal.setCounterparty(original.getCounterparty());
        reversal.setSourceDomain("REVERSAL");reversal.setSourceType("SYSTEM");reversal.setSourceId(String.valueOf(original.getFactId()));
        reversal.setStatus("CONFIRMED");reversal.setReversalFactId(original.getFactId());reversal.setIdempotencyKey("REVERSAL-"+original.getFactId());
        reversal.setConfirmedUserId(userId);reversal.setConfirmedUserName(userName);reversal.setConfirmedTime(new Date());
        reversal.setCreateUserId(userId);reversal.setCreateBy(userName);reversal.setRemark(reason.trim());
        mapper.insertFact(reversal);
        return reversal;
    }

    @Override
    @Transactional
    public Map<String,Object> recalculate(Long projectId,Date bizDate,Long userId,String userName,boolean viewAll)
    {
        Map<String,Object> project=requireProject(projectId,userId,viewAll);
        ensureAccountingOpen(project);
        ensureBusinessDate(project,bizDate,true);
        if(BusinessMemberDayCostService.POLICY.equals(project.get("costPolicyVersion")))memberDays.synchronize(projectId);
        return recalculateInternal(projectId,bizDate,userName);
    }

    @Override
    @Transactional
    public Map<String,Object> recalculatePersonnelCost(Long projectId,Date bizDate,String userName)
    {
        if(bizDate==null)throw new ServiceException("人员成本核算日期不能为空");
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(projectId);
        ensureAccountingOpen(project);
        ensureBusinessDate(project,bizDate,false);
        return recalculateInternal(projectId,bizDate,userName);
    }

    @Override
    public void ensureProjectCanClose(Long projectId)
    {
        if(publicExpenses!=null&&publicExpenses.countProjectPending(projectId)>0)
            throw new ServiceException("项目相关月份的公司公共费用尚未完成分摊或月结，请先在公司公共费用中处理");
        if(mapper.countProjectUnsettledFacts(projectId)>0)
            throw new ServiceException("项目仍有待确认或已退回未修改的收支，请处理完成后再结项");
        Map<String,Object> project=mapper.selectProjectForAccounting(projectId);
        if(project!=null&&BusinessMemberDayCostService.POLICY.equals(project.get("costPolicyVersion")))
        {
            memberDays.synchronize(projectId);
            if(memberDays.pending(projectId)>0)throw new ServiceException("成员工作日成本尚未计算完整，请完善工作日历和用人成本");
        }
        if(project!=null&&"ACTUAL_WORK_V1".equals(project.get("costPolicyVersion")))
        {
            if(workMapper.countPendingWork(projectId)>0)throw new ServiceException("项目仍有未处理完的实际工作记录");
            if(workMapper.countPendingCosts(projectId)>0)throw new ServiceException("已确认工作尚待计价或核算，请补齐费率并重试");
        }
    }

    @Override
    @Transactional
    public Map<String,Object> recalculatePublicExpenseCost(Long projectId,Date bizDate,String userName)
    {
        // Internal reclassification may clear a previously estimated day after an actual end-date correction.
        if(bizDate==null)throw new ServiceException("公共费用日期不能为空");
        ensureNotFuture(bizDate);
        return recalculateInternal(projectId,bizDate,userName);
    }

    @Override
    @Transactional
    public void closeProjectAccounting(Long projectId,Date closeDate,String userName)
    {
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(projectId);
        ensureAccountingOpen(project);
        ensureBusinessDate(project,closeDate,true);
        ensureProjectCanClose(projectId);
        if("ACTUAL_WORK_V1".equals(project.get("costPolicyVersion")))
            for(String date:workMapper.selectConfirmedWorkDates(projectId))recalculateInternal(projectId,DateUtils.parseDate(date),userName);
        recalculateInternal(projectId,closeDate,userName);
        if(profitTax!=null)profitTax.freeze(projectId,userName);
        mapper.closeProjectDailyResults(projectId,userName);
    }

    @Override
    @Transactional
    public BusinessOperatingFact recordProjectBonus(Long projectId,Date bizDate,BigDecimal amount,
        Long settlementId,Long userId,String userName)
    {
        if(projectId==null||bizDate==null||settlementId==null)throw new ServiceException("项目奖金入账参数不完整");
        if(amount==null||amount.compareTo(BigDecimal.ZERO)<0)throw new ServiceException("项目奖金不能为负数");
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(projectId);
        if(project==null)throw new ServiceException("项目不存在");
        String idempotencyKey="KPI-BONUS-SETTLEMENT-"+settlementId;
        BusinessOperatingFact existing=mapper.selectFactByIdempotencyKey(idempotencyKey);
        if(existing!=null)
        {
            if(!projectId.equals(existing.getProjectId()))throw new ServiceException("奖金结算不属于当前项目");
            return existing;
        }
        ensureAccountingOpen(project);
        ensureNotFuture(bizDate);
        if(BusinessProjectLifecycle.isSeparated(project))
        {
            Map<String,Object> settlement=mapper.selectProjectBonusSettlement(settlementId);
            if(settlement==null||!projectId.equals(longValue(settlement.get("projectId")))
                ||!sameDay(bizDate,dateValue(settlement.get("periodEnd")))
                ||!Arrays.asList("SUBMITTED","CONFIRMED").contains(String.valueOf(settlement.get("status"))))
                throw new ServiceException("奖金只能按该项目既有KPI结算周期入账");
        }
        if(amount.compareTo(BigDecimal.ZERO)==0)return null;
        if(project.get("companyDeptId")==null)throw new ServiceException("项目未设置归属公司");
        if(!"CNY".equalsIgnoreCase(String.valueOf(project.get("currency"))))
            throw new ServiceException("人民币奖金只能计入本位币为 CNY 的项目");
        Map<String,Object> category=mapper.selectCategoryByCode("PROJECT_BONUS_COST");
        if(category==null)throw new ServiceException("项目绩效奖金成本类别尚未初始化");
        BusinessOperatingFact fact=new BusinessOperatingFact();
        fact.setProjectId(projectId);fact.setCompanyDeptId(longValue(project.get("companyDeptId")));fact.setBizDate(bizDate);
        fact.setCategoryId(longValue(category.get("categoryId")));fact.setCategoryCode("PROJECT_BONUS_COST");
        fact.setCategoryName(String.valueOf(category.get("categoryName")));fact.setFactKind("COST");fact.setAmount(amount);
        fact.setCurrency("CNY");fact.setDescription("项目KPI奖金池结算 #"+settlementId);
        fact.setSourceDomain("KPI");fact.setSourceType("PROJECT_BONUS");fact.setSourceId(String.valueOf(settlementId));
        fact.setStatus("CONFIRMED");fact.setIdempotencyKey(idempotencyKey);fact.setConfirmedUserId(userId);
        fact.setConfirmedUserName(userName);fact.setConfirmedTime(new Date());fact.setCreateUserId(userId);fact.setCreateBy(userName);
        mapper.insertFact(fact);recalculateInternal(projectId,bizDate,userName);return fact;
    }

    @Override
    public Map<String,Object> resultDetail(Long resultId,Long userId,boolean viewAll)
    {
        Map<String,Object> query=new HashMap<String,Object>();query.put("userId",userId);query.put("viewAll",viewAll);
        List<Map<String,Object>> rows=mapper.selectDailyResults(query);Map<String,Object> found=null;
        for(Map<String,Object> row:rows)if(String.valueOf(resultId).equals(String.valueOf(row.get("resultId")))){found=row;break;}
        if(found==null)throw new ServiceException("日结果不存在或无权查看");
        if(profitTax!=null){Map<String,Object> taxResult=new HashMap<>();taxResult.put("results",rows);profitTax.decorate(taxResult,query);}
        List<Map<String,Object>> allItems=mapper.selectDailyResultItems(resultId);
        boolean rawCostVisible=com.ruoyi.common.utils.SecurityUtils.isAdmin(userId);
        if(!rawCostVisible)try{rawCostVisible=com.ruoyi.common.utils.SecurityUtils.hasPermi("business:staff:cost");}
        catch(ServiceException ex){rawCostVisible=false;}
        List<Map<String,Object>> items=new java.util.ArrayList<Map<String,Object>>();
        List<Map<String,Object>> personnelItems=new java.util.ArrayList<Map<String,Object>>();
        if(allItems!=null)for(Map<String,Object> item:allItems)
        {
            if("PERSONNEL_COST_PERSON".equals(String.valueOf(item.get("componentCode"))))
            {
                if(!rawCostVisible)
                {
                    item=new LinkedHashMap<String,Object>(item);
                    for(String field:Arrays.asList("basisJson","rateSnapshot","monthlyCost","dailyCost","hourlyCost","unitCost","standardWorkDays","policyId"))item.remove(field);
                    item.put("calculationDetail","按已保存的项目成本依据计价；内部费率需另获成本查看权限");
                }
                personnelItems.add(item);
            }
            else items.add(item);
        }
        found.put("items",items);found.put("personnelItems",personnelItems);found.put("rawCostVisible",rawCostVisible);return found;
    }

    private Map<String,Object> recalculateInternal(Long projectId,Date bizDate,String userName)
    {
        // Accounting tables store a business day, not a timestamp. Normalizing here keeps every
        // caller on the same key when selecting the next snapshot version and retiring the old one.
        bizDate = java.sql.Date.valueOf(DateUtils.parseDateToStr("yyyy-MM-dd", bizDate));
        Map<String,Object> project=mapper.selectProjectForAccountingForUpdate(projectId);
        if(project==null||project.get("companyDeptId")==null)throw new ServiceException("项目不存在或未设置归属公司");
        ensureAccountingOpen(project);
        Map<String,Object> sums=mapper.sumProjectFacts(projectId,bizDate);
        BigDecimal revenue=decimal(sums.get("revenueAmount")),cost=decimal(sums.get("costAmount"));
        BigDecimal bonus=decimal(sums.get("bonusCost")),publicCost=decimal(sums.get("publicCost"));
        Map<String,Object> dailyPublic=publicExpenses==null?null:publicExpenses.sumDailyCost(projectId,bizDate);
        BigDecimal publicEstimated=BigDecimal.ZERO;
        if(dailyPublic!=null){publicCost=publicCost.subtract(decimal(dailyPublic.get("monthlyFactAmount"))).add(decimal(dailyPublic.get("amount")));publicEstimated=decimal(dailyPublic.get("estimatedAmount"));}
        BigDecimal adjustment=decimal(sums.get("adjustmentAmount")),value=decimal(sums.get("valueScore"));
        boolean actualWork="ACTUAL_WORK_V1".equals(project.get("costPolicyVersion"));
        boolean automaticDays=BusinessMemberDayCostService.POLICY.equals(project.get("costPolicyVersion"));
        List<Map<String,Object>> actualItems=automaticDays?memberDays.dayCosts(projectId,bizDate):actualWork?workMapper.selectWorkCosts(projectId,bizDate):null;
        BigDecimal personnel=BigDecimal.ZERO;
        int pendingPersonnel=0;
        if(actualWork||automaticDays)
        {
            if(actualItems!=null)for(Map<String,Object> item:actualItems)
                if("PRICED".equals(item.get("pricingStatus"))&&item.get("amount")!=null)personnel=personnel.add(decimal(item.get("amount")));
                else pendingPersonnel++;
        }
        else personnel=decimal(mapper.sumProjectPersonnelCost(projectId,bizDate));
        BigDecimal profit=revenue.subtract(cost).subtract(personnel).subtract(bonus).subtract(publicCost).add(adjustment);
        Map<String,Object> result=new HashMap<String,Object>();result.put("projectId",projectId);
        result.put("companyDeptId",project.get("companyDeptId"));result.put("bizDate",bizDate);
        result.put("accountingMode",project.get("accountingMode"));result.put("revenueAmount",revenue);
        result.put("costAmount",cost);result.put("personnelCost",personnel);result.put("bonusCost",bonus);result.put("adjustmentAmount",adjustment);
        result.put("publicCost",publicCost);result.put("publicEstimatedCost",publicEstimated);
        // The current result must become effective before cumulative cost is read. Reading first would either
        // omit today's personnel cost or reuse the retired snapshot from a previous recalculation.
        result.put("profitAmount",profit);result.put("budgetSpent",BigDecimal.ZERO);
        result.put("valueScore",value);result.put("resultVersion",mapper.selectNextResultVersion(projectId,bizDate));
        result.put("calculationDetail","收入 - 业务成本 - 内部人员成本 - 项目绩效奖金 - 公共费用（日分摊，含暂估） + 核算调整；月结确认金额，不重复扣费");
        result.put("createBy",userName);mapper.retireCurrentResult(projectId,bizDate);mapper.insertDailyResult(result);
        result.put("pendingPersonnelCount",pendingPersonnel);result.put("personnelPricingStatus",pendingPersonnel>0?"PENDING":"PRICED");
        BigDecimal budgetSpent=budgetSpent(projectId,bizDate);
        result.put("budgetSpent",budgetSpent);
        mapper.updateDailyResultBudgetSpent(longValue(result.get("resultId")),budgetSpent);
        refreshLaterBudgetSnapshots(projectId,bizDate);
        addItem(result,"REVENUE","确认收入",revenue,"已确认收入经营事实合计");
        addItem(result,"BUSINESS_COST","业务成本",cost,"已确认成本经营事实合计");
        addItem(result,"PERSONNEL_COST","内部人员成本",personnel,automaticDays?"成员参与期间有效工作日 × 当日有效日成本 × 项目投入权重；无需填报工时。待完善："+pendingPersonnel:actualWork
            ? "仅汇总已确认且已计价工作；计划不代替实际。待计价记录："+pendingPersonnel
            : "按当日生效的成本政策和项目投入计算；已确认实际投入优先，否则使用计划投入");
        List<Map<String,Object>> personnelItems=actualWork||automaticDays?actualItems:mapper.selectProjectPersonnelCostDetails(projectId,bizDate);
        if(personnelItems!=null)for(Map<String,Object> personnelItem:personnelItems)
            if(!(actualWork||automaticDays)||"PRICED".equals(personnelItem.get("pricingStatus")))
            addItem(result,"PERSONNEL_COST_PERSON",String.valueOf(personnelItem.get("componentName")),
                decimal(personnelItem.get("amount")),String.valueOf(personnelItem.get("calculationDetail")));
        addItem(result,"PROJECT_BONUS_COST","项目绩效奖金",bonus,"独立奖励核准后由核算确认，或依历史KPI方案确认；不代表已向个人发放");
        if(publicCost.signum()!=0)addItem(result,"COMPANY_PUBLIC_COST","公司公共费用",publicCost,"公共人员成本按月承担额除以21.75暂估；日常公共费用按承担期间自然日分摊。月结以实际月额替换估算，不重复扣费。包含暂估："+publicEstimated.toPlainString());
        if(publicEstimated.signum()!=0)addItem(result,"PUBLIC_COST_ESTIMATED","其中：公共费用暂估",publicEstimated,"已包含在公司公共费用中，请勿再次相加；月结后确认实际金额");
        addItem(result,"ADJUSTMENT","核算调整",adjustment,"已确认调整事实合计");
        return result;
    }

    @Override
    public Map<String,Object> projectDashboard(Long projectId,Map<String,Object> query,Long userId,boolean viewAll)
    {
        Map<String,Object> project=mapper.selectProjectForAccounting(projectId);
        if(project==null)throw new ServiceException("项目不存在");
        boolean owner=String.valueOf(userId).equals(String.valueOf(project.get("mainOwnerUserId")));
        boolean parentOwner=userId!=null&&project.get("parentId")!=null
            &&String.valueOf(userId).equals(String.valueOf(project.get("parentMainOwnerUserId")));
        if(!viewAll&&!owner&&!parentOwner&&!companyAccess.project(projectId,userId))throw new ServiceException("无权查看该项目经营数据");
        Map<String,Object> scoped=query==null?new HashMap<String,Object>():new HashMap<String,Object>(query);
        scoped.put("projectId",projectId);
        // The project boundary was explicitly checked above. The generic dashboard's non-admin
        // scope is owner-company based, so it must not be applied to a project owner's cockpit.
        Map<String,Object> result=dashboard(scoped,userId,true);
        // The explicit project authorization does not grant the global project/company option directory.
        List<Map<String,Object>> allowedProjects=new java.util.ArrayList<Map<String,Object>>();
        Object projectOptions=result.get("projects");
        if(projectOptions instanceof List)for(Map<String,Object> option:(List<Map<String,Object>>)projectOptions)
            if(String.valueOf(projectId).equals(String.valueOf(option.get("projectId"))))allowedProjects.add(option);
        result.put("projects",allowedProjects);
        List<Map<String,Object>> allowedCompanies=new java.util.ArrayList<Map<String,Object>>();
        Object companyOptions=result.get("companies");
        if(project.get("companyDeptId")!=null&&companyOptions instanceof List)for(Map<String,Object> option:(List<Map<String,Object>>)companyOptions)
            if(String.valueOf(project.get("companyDeptId")).equals(String.valueOf(option.get("companyDeptId"))))allowedCompanies.add(option);
        result.put("companies",allowedCompanies);
        result.put("costPolicyVersion",project.get("costPolicyVersion"));
        result.put("pendingCostCount",BusinessMemberDayCostService.POLICY.equals(project.get("costPolicyVersion"))?memberDays.pending(projectId):"ACTUAL_WORK_V1".equals(project.get("costPolicyVersion"))?workMapper.countPendingCosts(projectId):0);
        Map<String,Object> budget=com.ruoyi.business.support.BusinessBudgetSnapshot.read(mapper.selectProjectBudgetSnapshot(projectId));
        if(budget!=null)
        {
            Date from=DateUtils.parseDate(budget.get("startDate")),to=DateUtils.parseDate(budget.get("endDate"));
            Date today=DateUtils.parseDate(DateUtils.getDate());
            result.put("budget",budget);
            result.put("budgetPeriodExpired",to!=null&&today.after(to));
            result.put("budgetPeriodSpent",from==null||to==null||today.before(from)?BigDecimal.ZERO:
                decimal(mapper.sumProjectCostInPeriod(projectId,from,today.after(to)?to:today)));
        }
        return result;
    }
    BigDecimal budgetSpent(Long projectId,Date bizDate)
    {
        Map<String,Object> budget=com.ruoyi.business.support.BusinessBudgetSnapshot.read(mapper.selectProjectBudgetSnapshot(projectId));
        if(budget==null||"PROJECT".equals(budget.get("cycle")))return decimal(mapper.sumProjectCostToDate(projectId,bizDate));
        Date from=DateUtils.parseDate(budget.get("startDate")),to=DateUtils.parseDate(budget.get("endDate"));
        if(from==null||to==null||bizDate.before(from)||bizDate.after(to))return BigDecimal.ZERO;
        return decimal(mapper.sumProjectCostInPeriod(projectId,from,bizDate));
    }

    private void refreshLaterBudgetSnapshots(Long projectId,Date changedDate)
    {
        List<Map<String,Object>> later=mapper.selectCurrentResultsAfter(projectId,changedDate);
        if(later==null)return;
        for(Map<String,Object> row:later)
        {
            Long resultId=longValue(row.get("resultId"));
            Date bizDate=row.get("bizDate") instanceof Date?(Date)row.get("bizDate"):DateUtils.parseDate(row.get("bizDate"));
            if(resultId!=null&&bizDate!=null)
                mapper.updateDailyResultBudgetSpent(resultId,budgetSpent(projectId,bizDate));
        }
    }
    private void addItem(Map<String,Object> result,String code,String name,BigDecimal amount,String detail)
    {Map<String,Object> item=new HashMap<String,Object>();item.put("resultId",result.get("resultId"));item.put("componentCode",code);item.put("componentName",name);item.put("amount",amount);item.put("calculationDetail",detail);mapper.insertDailyResultItem(item);}
    private Map<String,Object> requireProject(Long id,Long userId,boolean viewAll){Map<String,Object> p=mapper.selectProjectForAccountingForUpdate(id);if(p==null)throw new ServiceException("项目不存在");if(!viewAll&&!companyAccess.project(id,userId))throw new ServiceException("无权核算未授权公司的项目");return p;}
    private BusinessOperatingFact requireFact(Long id,Long userId,boolean viewAll)
    {
        BusinessOperatingFact f=mapper.selectFactById(id);
        if(f==null)throw new ServiceException("收支流水不存在");
        requireProject(f.getProjectId(),userId,viewAll);
        // The project lock clears cached reads; reload the fact after any competing write completed.
        return mapper.selectFactByIdForUpdate(id);
    }
    private Map<String,Object> requireContributorProject(Long id,Long userId,boolean viewAll)
    {
        Map<String,Object> p=mapper.selectProjectForAccountingForUpdate(id);
        if(p==null)throw new ServiceException("项目不存在");
        boolean owner=String.valueOf(userId).equals(String.valueOf(p.get("mainOwnerUserId")));
        if(!viewAll&&!owner)throw new ServiceException("只有项目主负责人可以提交项目今日收入和成果");
        return p;
    }
    private void ensureAccountingOpen(Map<String,Object> project)
    {
        BusinessProjectLifecycle.requireAccountingOpen(project);
    }
    private void ensureBusinessDate(Map<String,Object> project,Date bizDate,boolean allowSettlementDate)
    {
        if(bizDate==null)throw new ServiceException("请选择业务日期");
        ensureNotFuture(bizDate);
        if(!isPostDeliverySettlement(project))return;
        Date end=dateValue(project.get("actualEndDate"));
        if(end==null)throw new ServiceException("项目缺少实际结束日期，请先核对关闭记录");
        if(day(bizDate).after(day(end))&&!(allowSettlementDate
            &&mapper.countProjectSettlementDate(longValue(project.get("projectId")),bizDate)>0))
            throw new ServiceException("交付后补报的业务日期不能晚于项目实际结束日期");
    }
    private boolean isPostDeliverySettlement(Map<String,Object> project)
    {
        return BusinessProjectLifecycle.isSeparated(project)
            &&Arrays.asList("CLOSED","CANCELED").contains(String.valueOf(project.get("status")));
    }
    private void ensureNotFuture(Date bizDate)
    {
        if(day(bizDate).after(day(new Date())))throw new ServiceException("业务日期不能晚于今天");
    }
    private Date day(Date value)
    { return java.sql.Date.valueOf(new SimpleDateFormat("yyyy-MM-dd").format(value)); }
    private boolean sameDay(Date left,Date right)
    { return left!=null&&right!=null&&day(left).equals(day(right)); }
    private BusinessOperatingFact requireContributorFact(Long id,Long userId,boolean viewAll)
    {
        BusinessOperatingFact fact=mapper.selectFactById(id);
        if(fact==null)throw new ServiceException("收支流水不存在");
        requireContributorProject(fact.getProjectId(),userId,viewAll);
        fact=mapper.selectFactByIdForUpdate(id);
        if(!viewAll&&!userId.equals(fact.getCreateUserId()))throw new ServiceException("只能修改自己提交的草稿");
        return fact;
    }
    private Map<String,Object> scope(Map<String,Object> q,Long userId,boolean viewAll){Map<String,Object> s=q==null?new HashMap<String,Object>():new HashMap<String,Object>(q);s.put("userId",userId);s.put("viewAll",viewAll);return s;}
    private BigDecimal decimal(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(v));}
    private Long longValue(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
    private Date dateValue(Object v){return v==null?null:v instanceof Date?(Date)v:DateUtils.parseDate(v);}
    private BigDecimal negate(BigDecimal v){return v==null?null:v.negate();}
    private ServiceException changed(){return new ServiceException("数据已被其他人修改，请刷新后重试");}
}
