package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
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

/** Immutable monthly snapshots; daily references never create accounting facts. */
@Service
public class BusinessPublicExpenseService
{
    @org.springframework.beans.factory.annotation.Autowired
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    private static final BigDecimal HUNDRED=new BigDecimal("100"),WORKDAYS=new BigDecimal("21.75");
    @Autowired private BusinessPublicExpenseMapper mapper;
    @Autowired private BusinessPublicPersonnelService personnel;
    @Autowired private BusinessPublicExpenseDailyService dailyCosts;
    @Autowired private BusinessProjectMapper projects;
    @Autowired private BusinessAccountingMapper accounting;
    @Autowired @Lazy private IBusinessAccountingService accountingService;

    public Map<String,Object> workspace(Long companyDeptId,String month,String currency,Long userId)
    {
        month=month(month).toString();currency=currency(currency);
        List<Map<String,Object>> companies=mapper.selectCompanies(userId);
        if(companyDeptId==null&&!companies.isEmpty())companyDeptId=id(companies.get(0).get("companyDeptId"));
        Map<String,Object> out=map("companies",companies,"companyDeptId",companyDeptId,"month",month,"currency",currency,"canManage",false);
        if(companyDeptId==null)return out;
        boolean allowed=false;for(Map<String,Object> company:companies)if(companyDeptId.equals(id(company.get("companyDeptId"))))allowed=true;
        if(!allowed)throw error("只有获授权的公司老板可以管理该公司的公共费用");
        Map<String,Object> bill=mapper.selectMonth(companyDeptId,month,currency);
        out.put("canManage",true);out.put("owners",mapper.selectOwners(companyDeptId));
        out.put("departments",mapper.selectDepartments(companyDeptId));
        out.put("projects",mapper.selectProjects(companyDeptId,null,month,currency));
        List<Map<String,Object>> policies=mapper.selectPolicies(companyDeptId);for(Map<String,Object> policy:policies)policy.put("estimated",truth(policy.get("estimated")));
        out.put("policies",policies);out.put("bill",bill==null?null:billView(bill));out.put("history",mapper.selectHistory(companyDeptId));
        out.put("events",mapper.selectEvents(companyDeptId,bill==null?null:id(bill.get("billId"))));return out;
    }

    public Map<String,Object> personnelPreview(Long companyId,String selectedMonth,String selectedCurrency,Long userId) {
        requireCompany(requiredId(companyId),userId);
        String period=month(selectedMonth).toString(),unit=currency(selectedCurrency);
        Map<String,Object> preview=personnel.automaticPreview(companyId,period,unit,personnelSource(mapper.selectMonth(companyId,period,unit)));
        preview.remove("businessFacts");return preview;
    }
    private Map<String,Object> personnelSource(Map<String,Object> bill) {
        return bill==null||bill.get("personnelSnapshot")==null?null:JSON.parseObject(text(bill.get("personnelSnapshot")));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> savePersonnel(Map<String,Object> input,Long userId,String userName) {
        Long companyId=requiredId(input.get("companyDeptId"));requireCompany(companyId,userId);
        String selectedMonth=month(required(input.get("month"),"月份",7)).toString(),selectedCurrency=currency(input.get("currency"));
        Map<String,Object> bill=mapper.selectMonth(companyId,selectedMonth,selectedCurrency);
        if(bill!=null){bill=lockBill(id(bill.get("billId")));version(bill,input);state(bill,"DRAFT");}
        else if(input.get("version")!=null)throw error("月账已变化，请刷新");
        if(input.containsKey("rows"))throw error("人员成本由系统自动计算，请刷新页面后设置分摊比例");
        Map<String,Object> snapshot=personnel.automaticSnapshot(companyId,selectedMonth,selectedCurrency,personnelSource(bill));
        if(bill==null) {
            Map<String,Object> generate=map("companyDeptId",companyId,"month",selectedMonth,"currency",selectedCurrency,"personnelInit",true);
            bill=generateMonth(generate,userId,userName);bill=lockBill(id(bill.get("billId")));
        }
        BigDecimal old=decimal(bill.get("personnelAmount")),amount=decimal(snapshot.get("publicAmount"));
        bill.put("personnelAmount",amount);bill.put("personnelSnapshot",JSON.toJSONString(snapshot));
        bill.put("totalAmount",decimal(bill.get("totalAmount")).subtract(old).add(amount));
        replaceOwners(bill,mapper.selectOwnerAllocations(id(bill.get("billId"))));touchBill(bill);
        return finishBill(id(bill.get("billId")),"PERSONNEL","按人员成本设置自动汇总未由项目承担的成本，更新分摊金额",userId,userName);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> savePolicy(Map<String,Object> input,Long userId,String userName)
    {
        Long companyId=requiredId(input.get("companyDeptId"));requireCompany(companyId,userId);
        Long policyId=id(input.get("policyId"));Map<String,Object> current=null;
        if(policyId!=null){current=mapper.selectPolicyForUpdate(policyId);if(current==null||!companyId.equals(id(current.get("companyDeptId"))))throw error("费用规则不存在");version(current,input);}
        String period=required(input.get("periodType"),"填写方式",12).toUpperCase(Locale.ROOT);
        if(!Arrays.asList("ANNUAL","MONTHLY").contains(period))throw error("请选择按年或按月填写");
        YearMonth start=month(required(input.get("startMonth"),"起始月份",7));
        YearMonth end="ANNUAL".equals(period)?start.plusMonths(11):month(required(input.get("endMonth"),"结束月份",7));
        if(end.isBefore(start)||end.isAfter(start.plusYears(30)))throw error("费用有效月份范围不正确");
        String status=input.get("status")==null?"ACTIVE":String.valueOf(input.get("status"));
        if(!Arrays.asList("ACTIVE","DISABLED").contains(status))throw error("费用状态不正确");
        String attachments=optional(input.get("attachmentUrls"),4000);
        for(String path:attachments.split(","))if(!path.isEmpty()&&(!path.startsWith("/profile/upload/")||path.contains("..")||path.contains("\\")))throw error("请上传有效的费用附件");
        Map<String,Object> row=map("policyId",policyId,"companyDeptId",companyId,"name",required(input.get("name"),"费用名称",100),
            "category",required(input.get("category"),"费用类别",40),"periodType",period,"amount",money(input.get("amount"),false),
            "currency",currency(input.get("currency")),"startMonth",start.toString(),"endMonth",end.toString(),
            "estimated",truth(input.get("estimated")),"status",status,"remark",optional(input.get("remark"),500),"attachmentUrls",attachments,
            "version",current==null?0:current.get("version"),"userName",userName);
        if(current==null)mapper.insertPolicy(row);else if(mapper.updatePolicy(row)!=1)throw error("费用规则已更新，请刷新");
        event(companyId,null,"POLICY",current==null?"新增费用规则":"更新规则，仅影响尚未生成的月份",row,userId,userName);
        return mapper.selectPolicyForUpdate(id(row.get("policyId")));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> generateMonth(Map<String,Object> input,Long userId,String userName)
    {
        Long companyId=requiredId(input.get("companyDeptId"));requireCompany(companyId,userId);
        String month=month(required(input.get("month"),"月份",7)).toString(),currency=currency(input.get("currency"));
        Map<String,Object> existing=mapper.selectMonth(companyId,month,currency);
        Set<Long> existingPolicies=new HashSet<>();
        if(existing!=null)
        {
            if(!truth(input.get("syncNewPolicies")))return billView(existing);
            version(existing,input);state(existing,"DRAFT");
            for(Map<String,Object> entry:mapper.selectEntries(id(existing.get("billId"))))existingPolicies.add(id(entry.get("policyId")));
        }
        List<Map<String,Object>> entries=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
        for(Map<String,Object> policy:mapper.selectPolicies(companyId))
        {
            if(existingPolicies.contains(id(policy.get("policyId")))||!"ACTIVE".equals(policy.get("status"))||!currency.equals(policy.get("currency"))||month.compareTo(text(policy.get("startMonth")))<0||month.compareTo(text(policy.get("endMonth")))>0)continue;
            Map<String,Object> entry=new LinkedHashMap<>(policy);BigDecimal amount=monthlyAmount(policy,YearMonth.parse(month));
            entry.put("amount",amount);entry.put("policyVersion",policy.get("version"));entries.add(entry);total=total.add(amount);
        }
        if(existing!=null)
        {
            if(entries.isEmpty())return billView(existing);
            Long existingId=id(existing.get("billId"));for(Map<String,Object> entry:entries){entry.put("billId",existingId);mapper.insertEntry(entry);}
            existing.put("totalAmount",decimal(existing.get("totalAmount")).add(total));
            replaceOwners(existing,mapper.selectOwnerAllocations(existingId));touchBill(existing);
            return finishBill(existingId,"SYNC_POLICIES","同步新增有效费用，保留已有月明细实际金额",userId,userName);
        }
        if(entries.isEmpty()&&!truth(input.get("personnelInit")))throw error("该月份和币种没有有效费用规则，请先新增费用");
        Map<String,Object> bill=map("companyDeptId",companyId,"month",month,"currency",currency,"totalAmount",total,"userName",userName);
        mapper.insertMonth(bill);Long billId=id(bill.get("billId"));
        for(Map<String,Object> entry:entries){entry.put("billId",billId);mapper.insertEntry(entry);}
        Map<String,Object> saved=billView(mapper.selectBill(billId));event(companyId,billId,"GENERATED","按有效规则生成月费用快照",saved,userId,userName);return saved;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> saveEntries(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);version(bill,input);state(bill,"DRAFT");
        List<Map<String,Object>> existing=mapper.selectEntries(billId),rows=rows(input,"entries");
        if(rows.size()!=existing.size())throw error("请完整提交当月费用明细");
        Map<Long,Map<String,Object>> valid=index(existing,"entryId");Set<Long> seen=new HashSet<>();BigDecimal total=BigDecimal.ZERO;
        for(Map<String,Object> item:rows)
        {
            Long entryId=requiredId(item.get("entryId"));if(!valid.containsKey(entryId)||!seen.add(entryId))throw error("费用明细重复或不存在");
            BigDecimal amount=money(item.get("amount"),false);total=total.add(amount);
            mapper.updateEntry(map("billId",billId,"entryId",entryId,"amount",amount,"estimated",truth(item.get("estimated")),"remark",optional(item.get("remark"),500)));
        }
        bill.put("totalAmount",total.add(decimal(bill.get("personnelAmount"))));touchBill(bill);
        // Changing the pool invalidates every previous monetary allocation.
        List<Map<String,Object>> allocations=mapper.selectOwnerAllocations(billId);
        replaceOwners(bill,allocations);return finishBill(billId,"ENTRIES","核实月费用并重新计算负责人金额",userId,userName);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> saveOwners(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);version(bill,input);state(bill,"DRAFT");
        String selectedPool=pool(input);
        List<Map<String,Object>> combined=new ArrayList<>();
        for(Map<String,Object> row:mapper.selectOwnerAllocations(billId))if(!selectedPool.equals(pool(row)))combined.add(row);
        for(Map<String,Object> row:rows(input,"allocations")){row.put("costPool",selectedPool);combined.add(row);}
        replaceOwners(bill,combined);touchBill(bill);
        return finishBill(billId,"OWNERS","保存负责人分配草稿",userId,userName);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> copyOwners(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);version(bill,input);state(bill,"DRAFT");
        Map<String,Object> previous=mapper.selectMonth(id(bill.get("companyDeptId")),month(text(bill.get("month"))).minusMonths(1).toString(),text(bill.get("currency")));
        if(previous==null)throw error("上月没有可复制的分配");
        Set<Long> valid=index(mapper.selectOwners(id(bill.get("companyDeptId"))),"userId").keySet();
        String selectedPool=pool(input);List<Map<String,Object>> selected=new ArrayList<>();
        for(Map<String,Object> row:mapper.selectOwnerAllocations(billId))if(!selectedPool.equals(pool(row)))selected.add(row);
        for(Map<String,Object> row:mapper.selectOwnerAllocations(id(previous.get("billId"))))if(selectedPool.equals(pool(row))&&valid.contains(id(row.get("ownerUserId")))){row.remove("deptId");selected.add(row);}
        replaceOwners(bill,selected);touchBill(bill);return finishBill(billId,"COPY_OWNERS","复制上月仍有效的负责人比例，请核对合计",userId,userName);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> publish(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);version(bill,input);state(bill,"DRAFT");
        List<Map<String,Object>> owners=mapper.selectOwnerAllocations(billId);completePools(bill,owners);
        Map<Long,Map<String,Object>> valid=index(mapper.selectOwners(id(bill.get("companyDeptId"))),"userId");
        for(Map<String,Object> owner:owners)if(!valid.containsKey(id(owner.get("ownerUserId"))))throw error("负责人已不属于该公司，请重新分配");
        bill.put("status","PUBLISHED");touchBill(bill);return finishBill(billId,"PUBLISHED","下发月费用给负责人",userId,userName);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> recall(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);version(bill,input);state(bill,"PUBLISHED");
        String reason=required(input.get("reason"),"退回原因",500);
        event(id(bill.get("companyDeptId")),billId,"BEFORE_RECALL",reason,billView(bill),userId,userName);
        mapper.deleteBillProjects(billId);
        for(Map<String,Object> owner:mapper.selectOwnerAllocations(billId)){owner.put("status","DRAFT");if(mapper.updateOwner(owner)!=1)throw error("负责人分摊已更新，请刷新");}
        bill.put("status","DRAFT");touchBill(bill);
        return finishBill(billId,"RECALLED",reason,userId,userName);
    }

    public Map<String,Object> ownerWorkspace(String month,Long userId)
    {
        String selected=month(month).toString();List<Map<String,Object>> out=new ArrayList<>();
        for(Map<String,Object> row:mapper.selectOwnerBills(userId,selected))out.add(ownerView(row,mapper.selectBill(id(row.get("billId")))));
        return map("month",selected,"bills",out);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> saveProjects(Long allocationId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> owner=owner(allocationId);Map<String,Object> bill=lockBill(id(owner.get("billId")));
        owner=owner(allocationId);requireOwner(owner,userId);version(owner,input);state(bill,"PUBLISHED");
        replaceProjects(owner,bill,rows(input,"allocations"));touchBill(bill);
        event(id(bill.get("companyDeptId")),id(bill.get("billId")),"PROJECTS","负责人保存项目分摊草稿",ownerView(owner(allocationId),bill),userId,userName);
        if(dailyCosts!=null)dailyCosts.synchronize(id(bill.get("billId")));
        return ownerView(owner(allocationId),bill);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> copyProjects(Long allocationId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> owner=owner(allocationId),bill=lockBill(id(owner.get("billId")));owner=owner(allocationId);
        requireOwner(owner,userId);version(owner,input);state(bill,"PUBLISHED");
        Map<String,Object> previous=mapper.selectMonth(id(bill.get("companyDeptId")),month(text(bill.get("month"))).minusMonths(1).toString(),text(bill.get("currency")));
        if(previous==null)throw error("上月没有可复制的项目分摊");
        Map<String,Object> old=null;for(Map<String,Object> row:mapper.selectOwnerAllocations(id(previous.get("billId"))))if(userId.equals(id(row.get("ownerUserId")))&&pool(owner).equals(pool(row)))old=row;
        if(old==null)throw error("上月没有本人的项目分摊");
        Set<Long> valid=index(projectOptions(owner,bill),"projectId").keySet();List<Map<String,Object>> selected=new ArrayList<>();
        for(Map<String,Object> row:mapper.selectProjectAllocations(id(old.get("allocationId"))))if(valid.contains(id(row.get("projectId"))))selected.add(row);
        replaceProjects(owner,bill,selected);touchBill(bill);
        event(id(bill.get("companyDeptId")),id(bill.get("billId")),"COPY_PROJECTS","复制上月仍有效的项目比例，请核对合计",selected,userId,userName);
        if(dailyCosts!=null)dailyCosts.synchronize(id(bill.get("billId")));
        return ownerView(owner(allocationId),bill);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> submit(Long allocationId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> owner=owner(allocationId),bill=lockBill(id(owner.get("billId")));owner=owner(allocationId);
        requireOwner(owner,userId);version(owner,input);state(bill,"PUBLISHED");
        List<Map<String,Object>> rows=mapper.selectProjectAllocations(allocationId);complete(rows,decimal(owner.get("amount")));
        for(Map<String,Object> row:rows)validateProject(requiredId(row.get("projectId")),bill,id(owner.get("ownerUserId")),true);
        owner.put("status","SUBMITTED");if(mapper.updateOwner(owner)!=1)throw error("分摊已更新，请刷新");touchBill(bill);
        event(id(bill.get("companyDeptId")),id(bill.get("billId")),"SUBMITTED","负责人提交项目分摊",ownerView(owner(allocationId),bill),userId,userName);
        if(dailyCosts!=null)dailyCosts.synchronize(id(bill.get("billId")));
        return ownerView(owner(allocationId),bill);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> settle(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);version(bill,input);state(bill,"PUBLISHED");
        if(!month(text(bill.get("month"))).isBefore(YearMonth.now()))throw error("月份结束后才能正式月结，日均金额仅供参考");
        for(Map<String,Object> entry:mapper.selectEntries(billId))if(truth(entry.get("estimated")))throw error("仍有暂估费用，请退回草稿核实实际费用后再月结");
        if(personnel!=null)personnel.validateSettlement(bill);
        List<Map<String,Object>> owners=mapper.selectOwnerAllocations(billId);completePools(bill,owners);
        // All project rows are locked in stable order before any accounting facts are written.
        List<Map<String,Object>> all=new ArrayList<>();
        for(Map<String,Object> owner:owners)
        {
            if(!"SUBMITTED".equals(owner.get("status"))&&decimal(owner.get("amount")).signum()>0)throw error("仍有负责人未提交项目分摊");
            List<Map<String,Object>> rows=mapper.selectProjectAllocations(id(owner.get("allocationId")));complete(rows,decimal(owner.get("amount")));
            all.addAll(rows);
        }
        all.sort(Comparator.comparing(row->id(row.get("projectId"))));
        for(Map<String,Object> row:all)
        {
            // Submission fixed the responsible person; later project handovers preserve that snapshot.
            BusinessProject project=validateProject(id(row.get("projectId")),bill,null,true);
            BigDecimal amount=decimal(row.get("amount"));if(amount.signum()==0)continue;
            Long factId=recordFact(bill,project,amount,"PUBLIC-EXPENSE-"+row.get("projectAllocationId"),text(row.get("projectAllocationId")),"公司公共费用月结",userId,userName);
            row.put("accountingFactId",factId);mapper.attachFact(row);
        }
        bill.put("status","SETTLED");bill.put("userId",userId);bill.put("userName",userName);touchBill(bill);
        return finishBill(billId,"SETTLED","月结确认每日成本，按实际金额核实差额",userId,userName);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> adjust(Long billId,Map<String,Object> input,Long userId,String userName)
    {
        Map<String,Object> bill=lockBill(billId);requireBoss(bill,userId);state(bill,"SETTLED");
        Long projectId=requiredId(input.get("projectId"));BigDecimal amount=money(input.get("amount"),true);
        if(amount.signum()==0)throw error("调整金额不能为0");String reason=required(input.get("reason"),"调整原因",500),key=required(input.get("requestKey"),"请求标识",64);
        Map<String,Object> duplicate=mapper.selectAdjustmentByRequest(billId,key);
        if(duplicate!=null)
        {
            if(!projectId.equals(id(duplicate.get("projectId")))||amount.compareTo(decimal(duplicate.get("amount")))!=0||!reason.equals(duplicate.get("reason")))throw error("该请求标识已用于不同的调整");
            return billView(bill);
        }
        version(bill,input);BigDecimal original=BigDecimal.ZERO;boolean found=false;
        for(Map<String,Object> owner:mapper.selectOwnerAllocations(billId))for(Map<String,Object> row:mapper.selectProjectAllocations(id(owner.get("allocationId"))))if(projectId.equals(id(row.get("projectId")))){found=true;original=original.add(decimal(row.get("amount")));}
        if(!found)throw error("只能调整该月已分摊的项目");
        for(Map<String,Object> adjustment:mapper.selectAdjustments(billId,projectId))original=original.add(decimal(adjustment.get("amount")));
        if(original.add(amount).signum()<0)throw error("调整后项目公共费用不能为负数");
        BusinessProject project=validateProject(projectId,bill,null,true);
        Map<String,Object> row=map("billId",billId,"projectId",projectId,"amount",amount,"reason",reason,"requestKey",key,"userId",userId,"userName",userName);
        mapper.insertAdjustment(row);Long factId=recordFact(bill,project,amount,"PUBLIC-EXPENSE-ADJUST-"+row.get("adjustmentId"),"ADJUST-"+row.get("adjustmentId"),"公共费用历史调整："+reason,userId,userName);
        row.put("accountingFactId",factId);mapper.attachAdjustmentFact(row);touchBill(bill);
        return finishBill(billId,"ADJUSTED",reason,userId,userName);
    }

    public Map<String,Object> projectWorkspace(Long projectId,String month,Long userId,boolean viewAll)
    {
        BusinessProject project=projects.selectProjectById(projectId);if(project==null||"2".equals(project.getDelFlag()))throw error("项目不存在");
        boolean leader=false;for(Map<String,Object> company:mapper.selectCompanies(userId))if(Objects.equals(id(company.get("companyDeptId")),project.getCompanyDeptId()))leader=true;
        if(!viewAll&&!leader&&!Objects.equals(userId,project.getMainOwnerUserId())&&!Objects.equals(userId,project.getSponsorOwnerUserId())&&!Objects.equals(userId,project.getInitiatorUserId())&&accounting.selectAccountingMemberRole(projectId,userId)==null)throw error("无权查看该项目公共费用");
        Map<String,Object> out=mapper.readProjectCosts(projectId,month(month).toString());if(out==null)out=map("monthAmount",BigDecimal.ZERO);
        List<Map<String,Object>> history=mapper.selectProjectHistory(projectId),selected=new ArrayList<>();String selectedMonth=month(month).toString();
        for(Map<String,Object> row:history)if(selectedMonth.equals(row.get("month")))selected.add(row);
        Map<String,Object> dailySummary=mapper.dailyProjectSummary(projectId,selectedMonth);
        if(dailySummary!=null){out.putAll(dailySummary);out.put("dailyRecognition",truth(dailySummary.get("dailyRecognition")));}
        out.put("dailyCosts",mapper.selectProjectDailyCosts(projectId,selectedMonth));
        out.put("hasPublishedBill",truth(out.get("hasPublishedBill")));out.put("dailyReference",daily(decimal(out.get("monthAmount"))));out.put("allocations",selected);out.put("history",history);out.put("adjustments",mapper.selectAdjustments(null,projectId));return out;
    }

    private static String pool(Map<String,Object> row) {
        String value=row.get("costPool")==null?"EXPENSE":String.valueOf(row.get("costPool"));
        if(!Arrays.asList("EXPENSE","PERSONNEL").contains(value))throw error("费用分摊类型不正确");return value;
    }
    private static BigDecimal poolAmount(Map<String,Object> bill,String pool) {
        BigDecimal personnel=decimal(bill.get("personnelAmount"));
        return "PERSONNEL".equals(pool)?personnel:decimal(bill.get("totalAmount")).subtract(personnel);
    }
    private static void completePools(Map<String,Object> bill,List<Map<String,Object>> rows) {
        for(String kind:Arrays.asList("EXPENSE","PERSONNEL")) {
            List<Map<String,Object>> selected=new ArrayList<>();for(Map<String,Object> row:rows)if(kind.equals(pool(row)))selected.add(row);
            complete(selected,poolAmount(bill,kind));
        }
    }
    private void replaceOwners(Map<String,Object> bill,List<Map<String,Object>> rows) {
        Map<Long,Map<String,Object>> valid=index(mapper.selectOwners(id(bill.get("companyDeptId"))),"userId");
        List<Map<String,Object>> inserts=new ArrayList<>();Long billId=id(bill.get("billId"));
        for(String kind:Arrays.asList("EXPENSE","PERSONNEL")) {
            List<Map<String,Object>> selected=new ArrayList<>();for(Map<String,Object> row:rows)if(kind.equals(pool(row)))selected.add(row);
            List<BigDecimal> percentages=percentages(selected,"ownerUserId"),amounts=allocate(poolAmount(bill,kind),percentages);
            for(int i=0;i<selected.size();i++) {
                Long userId=requiredId(selected.get(i).get("ownerUserId"));Map<String,Object> owner=valid.get(userId);
                if(owner==null)throw error("请选择本公司的有效负责人");
                if(selected.get(i).get("deptId")!=null&&!Objects.equals(id(owner.get("deptId")),id(selected.get(i).get("deptId"))))throw error("负责人所属部门已变化，请重新选择部门");
                inserts.add(map("billId",billId,"costPool",kind,"ownerUserId",userId,"ownerName",owner.get("userName"),"deptId",owner.get("deptId"),"deptName",owner.get("deptName"),"percentage",percentages.get(i),"amount",amounts.get(i)));
            }
        }
        mapper.deleteBillProjects(billId);mapper.deleteOwners(billId);for(Map<String,Object> row:inserts)mapper.insertOwner(row);
    }

    private void replaceProjects(Map<String,Object> owner,Map<String,Object> bill,List<Map<String,Object>> rows)
    {
        List<BigDecimal> percentages=percentages(rows,"projectId"),amounts=allocate(decimal(owner.get("amount")),percentages);
        List<BusinessProject> selected=new ArrayList<>();for(Map<String,Object> row:rows)selected.add(validateProject(requiredId(row.get("projectId")),bill,id(owner.get("ownerUserId")),true));
        mapper.deleteOwnerProjects(id(owner.get("allocationId")));
        for(int i=0;i<rows.size();i++)mapper.insertProject(map("allocationId",owner.get("allocationId"),"projectId",selected.get(i).getProjectId(),"projectName",selected.get(i).getProjectName(),"percentage",percentages.get(i),"amount",amounts.get(i)));
        owner.put("status","DRAFT");if(mapper.updateOwner(owner)!=1)throw error("分摊已更新，请刷新");
    }

    private BusinessProject validateProject(Long projectId,Map<String,Object> bill,Long ownerUserId,boolean lock)
    {
        BusinessProject project=lock?projects.selectProjectByIdForUpdate(projectId):projects.selectProjectById(projectId);
        if(project==null||!"0".equals(project.getDelFlag())||!Objects.equals(project.getCompanyDeptId(),id(bill.get("companyDeptId"))))throw error("分摊项目不存在或不属于该公司");
        if(ownerUserId!=null&&!ownerUserId.equals(project.getMainOwnerUserId()))throw error("只能分摊给本人担任主负责人的项目");
        if(!Objects.equals(project.getBaseCurrency(),bill.get("currency")))throw error("费用币种必须与项目核算币种一致");
        if("CLOSED".equals(project.getAccountingState())||(!"SEPARATED_V1".equals(project.getDeliveryPolicyVersion())&&Arrays.asList("CLOSED","CANCELED").contains(project.getStatus())))throw error("项目核算已关闭，不能新增或调整公共费用");
        YearMonth month=month(text(bill.get("month")));Date start=project.getActualStartDate()==null?project.getPlanStartDate():project.getActualStartDate();
        if(start!=null&&date(start).isAfter(month.atEndOfMonth()))throw error("项目尚未开始，不能分摊该月费用");
        if(project.getActualEndDate()!=null&&date(project.getActualEndDate()).isBefore(month.atDay(1)))throw error("项目在该月之前已结束，不能分摊该月费用");return project;
    }

    private Long recordFact(Map<String,Object> bill,BusinessProject project,BigDecimal amount,String key,String line,String description,Long userId,String userName)
    {
        if(accounting.selectFactByIdempotencyKey(key)!=null)throw error("该公共费用已入账，请刷新核对");
        Map<String,Object> category=accounting.selectCategoryByCode("COMPANY_PUBLIC_COST");if(category==null)throw error("公共费用类别尚未初始化，请先执行数据库迁移");
        LocalDate day=month(text(bill.get("month"))).atEndOfMonth();if(project.getActualEndDate()!=null&&date(project.getActualEndDate()).isBefore(day))day=date(project.getActualEndDate());
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setProjectId(project.getProjectId());fact.setCompanyDeptId(project.getCompanyDeptId());fact.setBizDate(java.sql.Date.valueOf(day));
        fact.setCategoryId(id(category.get("categoryId")));fact.setCategoryCode("COMPANY_PUBLIC_COST");fact.setCategoryName("公司公共费用");fact.setFactKind("COST");fact.setAmount(amount);fact.setCurrency(text(bill.get("currency")));
        fact.setDescription((bill.get("month")+" "+description).substring(0,Math.min(500,(bill.get("month")+" "+description).length())));fact.setSourceDomain("COMPANY");fact.setSourceType("PUBLIC_EXPENSE");fact.setSourceId(text(bill.get("billId")));fact.setSourceLineKey(line);
        fact.setStatus("CONFIRMED");fact.setIdempotencyKey(key);fact.setConfirmedUserId(userId);fact.setConfirmedUserName(userName);fact.setConfirmedTime(new Date());fact.setCreateUserId(userId);fact.setCreateBy(userName);fact.setRemark("DAILY_V1".equals(bill.get("recognitionMode"))?"月结确认凭据；经营成本采用每日分摊金额，不额外扣除本月总额":"历史账单按月结金额计入成本");
        accounting.insertFact(fact);accountingService.recalculatePersonnelCost(project.getProjectId(),fact.getBizDate(),userName);return fact.getFactId();
    }

    private Map<String,Object> billView(Map<String,Object> bill)
    {
        Map<String,Object> out=new LinkedHashMap<>(bill);if(bill.get("personnelSnapshot")!=null)out.put("personnel",JSON.parseObject(text(bill.get("personnelSnapshot"))));out.remove("personnelSnapshot");Long billId=id(bill.get("billId"));List<Map<String,Object>> entries=mapper.selectEntries(billId);
        for(Map<String,Object> entry:entries)entry.put("estimated",truth(entry.get("estimated")));out.put("entries",entries);
        List<Map<String,Object>> owners=new ArrayList<>();for(Map<String,Object> owner:mapper.selectOwnerAllocations(billId))owners.add(ownerView(owner,bill));
        List<Map<String,Object>> adjustments=mapper.selectAdjustments(billId,null);BigDecimal delta=BigDecimal.ZERO;
        for(Map<String,Object> adjustment:adjustments)delta=delta.add(decimal(adjustment.get("amount")));
        out.put("ownerAllocations",owners);out.put("adjustments",adjustments);out.put("adjustmentAmount",delta);out.put("adjustedTotalAmount",decimal(bill.get("totalAmount")).add(delta));return out;
    }

    private Map<String,Object> ownerView(Map<String,Object> owner,Map<String,Object> bill)
    {
        Map<String,Object> out=new LinkedHashMap<>(owner);for(String field:Arrays.asList("companyDeptId","companyName","month","currency","totalAmount"))out.put(field,bill.get(field));out.put("billStatus",bill.get("status"));
        List<Map<String,Object>> rows=mapper.selectProjectAllocations(id(owner.get("allocationId")));BigDecimal allocated=BigDecimal.ZERO;for(Map<String,Object> row:rows)allocated=allocated.add(decimal(row.get("amount")));
        out.put("projects",rows);out.put("allocatedAmount",allocated);out.put("remainingAmount",decimal(owner.get("amount")).subtract(allocated));out.put("dailyReference",daily(decimal(owner.get("amount"))));out.put("projectOptions",projectOptions(owner,bill));
        String kind=pool(owner);out.put("costPool",kind);out.put("totalAmount",poolAmount(bill,kind));
        List<Map<String,Object>> entries="PERSONNEL".equals(kind)?new ArrayList<>(Collections.singletonList(map("name","公共人员成本","category","PERSONNEL","amount",poolAmount(bill,kind),"estimated",!"SETTLED".equals(bill.get("status"))))):mapper.selectEntries(id(bill.get("billId")));
        List<BigDecimal> rates=new ArrayList<>();BigDecimal left=HUNDRED,total=poolAmount(bill,kind);
        for(int i=0;i<entries.size();i++){BigDecimal rate=total.signum()==0?BigDecimal.ZERO:(i==entries.size()-1?left:decimal(entries.get(i).get("amount")).multiply(HUNDRED).divide(total,16,RoundingMode.DOWN));rates.add(rate);left=left.subtract(rate);}
        List<BigDecimal> amounts=allocate(decimal(owner.get("amount")),rates);
        for(int i=0;i<entries.size();i++){entries.get(i).put("estimated",truth(entries.get(i).get("estimated")));entries.get(i).put("ownerAmount",amounts.get(i));}out.put("entries",entries);return out;
    }
    private List<Map<String,Object>> projectOptions(Map<String,Object> owner,Map<String,Object> bill){return mapper.selectProjects(id(bill.get("companyDeptId")),id(owner.get("ownerUserId")),text(bill.get("month")),text(bill.get("currency")));}
    private Map<String,Object> finishBill(Long billId,String type,String reason,Long userId,String userName){if(dailyCosts!=null)dailyCosts.synchronize(billId);Map<String,Object> out=billView(mapper.selectBill(billId));event(id(out.get("companyDeptId")),billId,type,reason,out,userId,userName);return out;}
    private void touchBill(Map<String,Object> bill){if(mapper.updateBill(bill)!=1)throw error("月费用已更新，请刷新后重试");bill.put("version",integer(bill.get("version"))+1);}
    private Map<String,Object> lockBill(Long billId){Map<String,Object> first=mapper.selectBill(billId);if(first==null)throw error("月费用不存在");mapper.selectCompanyForUpdate(id(first.get("companyDeptId")));return mapper.selectBillForUpdate(billId);}
    private void requireCompany(Long companyId,Long userId){Map<String,Object> company=mapper.selectCompanyForUpdate(companyId);if(company==null||!companyAccess.allowed(userId,companyId,"BUSINESS"))throw error("只有获授权的公司老板可以管理该公司的公共费用");}
    private void requireBoss(Map<String,Object> bill,Long userId){requireCompany(id(bill.get("companyDeptId")),userId);}
    private Map<String,Object> owner(Long allocationId){Map<String,Object> out=mapper.selectOwner(allocationId);if(out==null)throw error("负责人分摊不存在或已被老板退回，请刷新");return out;}
    private void requireOwner(Map<String,Object> owner,Long userId){if(!Objects.equals(userId,id(owner.get("ownerUserId"))))throw error("只有该负责人本人可以分配和提交项目费用");}
    private void event(Long companyId,Long billId,String type,String reason,Object snapshot,Long userId,String userName){mapper.insertEvent(map("companyDeptId",companyId,"billId",billId,"eventType",type,"reason",reason,"snapshot",JSON.toJSONString(snapshot),"userId",userId,"userName",userName));}

    static BigDecimal monthlyAmount(Map<String,Object> policy,YearMonth selected)
    {
        BigDecimal amount=decimal(policy.get("amount"));if(!"ANNUAL".equals(policy.get("periodType")))return amount.setScale(2,RoundingMode.UNNECESSARY);
        YearMonth start=YearMonth.parse(text(policy.get("startMonth")));BigDecimal normal=amount.divide(new BigDecimal("12"),2,RoundingMode.DOWN);
        return selected.equals(start.plusMonths(11))?amount.subtract(normal.multiply(new BigDecimal("11"))):normal;
    }
    /** Largest remainder allocation conserves every cent without making a tiny last share negative. */
    static List<BigDecimal> allocate(BigDecimal amount,List<BigDecimal> rates)
    {
        BigDecimal total=BigDecimal.ZERO;for(BigDecimal rate:rates)total=total.add(rate);if(total.compareTo(HUNDRED)>0)throw error("分配比例合计不能超过100%");
        List<BigDecimal> out=new ArrayList<>(),remainders=new ArrayList<>();BigDecimal allocated=BigDecimal.ZERO;
        BigDecimal target=amount.multiply(total).divide(HUNDRED,2,RoundingMode.HALF_UP);
        for(BigDecimal rate:rates){BigDecimal exact=amount.multiply(rate).divide(HUNDRED),rounded=exact.setScale(2,RoundingMode.DOWN);out.add(rounded);remainders.add(exact.subtract(rounded));allocated=allocated.add(rounded);}
        int cents=target.subtract(allocated).movePointRight(2).intValueExact();List<Integer> order=new ArrayList<>();for(int i=0;i<rates.size();i++)order.add(i);order.sort((a,b)->remainders.get(b).compareTo(remainders.get(a)));for(int i=0;i<cents;i++){int index=order.get(i);out.set(index,out.get(index).add(new BigDecimal("0.01")));}
        return out;
    }
    private static List<BigDecimal> percentages(List<Map<String,Object>> rows,String idField){Set<Long> seen=new HashSet<>();List<BigDecimal> out=new ArrayList<>();for(Map<String,Object> row:rows){if(!seen.add(requiredId(row.get(idField))))throw error("分摊对象不能重复");BigDecimal rate=decimal(row.get("percentage"));if(rate.signum()<0||rate.compareTo(HUNDRED)>0||rate.scale()>4)throw error("比例须为0至100，最多四位小数");out.add(rate);}return out;}
    private static void complete(List<Map<String,Object>> rows,BigDecimal expected){if(rows.isEmpty()&&expected.signum()==0)return;BigDecimal rates=BigDecimal.ZERO,total=BigDecimal.ZERO;for(Map<String,Object> row:rows){rates=rates.add(decimal(row.get("percentage")));total=total.add(decimal(row.get("amount")));}if(rates.compareTo(HUNDRED)!=0||total.compareTo(expected)!=0)throw error("请将比例分配到100%，并确保分摊金额与费用总额一致");}
    private static void version(Map<String,Object> current,Map<String,Object> input){if(input.get("version")==null||!Objects.equals(integer(current.get("version")),integer(input.get("version"))))throw error("数据已更新，请刷新后重试");}
    private static void state(Map<String,Object> row,String expected){if(!expected.equals(row.get("status")))throw error("当前状态不能执行此操作，请刷新");}
    private static YearMonth month(String value){try{return value==null||value.trim().isEmpty()?YearMonth.now():YearMonth.parse(value);}catch(RuntimeException ex){throw error("月份格式应为YYYY-MM");}}
    private static LocalDate date(Date value){return LocalDate.parse(DateUtils.parseDateToStr("yyyy-MM-dd",value));}
    private static String currency(Object value){String currency=value==null?"CNY":String.valueOf(value).trim().toUpperCase(Locale.ROOT);if(!currency.matches("[A-Z]{3}"))throw error("币种须为三位字母代码");return currency;}
    private static BigDecimal daily(BigDecimal value){return value.divide(WORKDAYS,2,RoundingMode.HALF_UP);}
    private static BigDecimal money(Object value,boolean signed){if(value==null)throw error("请填写金额");BigDecimal amount=decimal(value);if((!signed&&amount.signum()<0)||amount.scale()>2||amount.precision()-amount.scale()>16)throw error("金额不正确，最多两位小数");return amount.setScale(2);}
    private static BigDecimal decimal(Object value){try{return value==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(value));}catch(RuntimeException ex){throw error("数字格式不正确");}}
    private static Integer integer(Object value){try{return value==null?null:Integer.valueOf(String.valueOf(value));}catch(RuntimeException ex){throw error("版本格式不正确");}}
    private static Long id(Object value){try{return value==null?null:Long.valueOf(String.valueOf(value));}catch(RuntimeException ex){throw error("编号格式不正确");}}
    private static Long requiredId(Object value){Long id=id(value);if(id==null||id<=0)throw error("请选择有效对象");return id;}
    private static boolean truth(Object value){return Boolean.TRUE.equals(value)||"1".equals(String.valueOf(value))||"true".equalsIgnoreCase(String.valueOf(value));}
    private static String text(Object value){return value==null?"":String.valueOf(value);}
    private static String optional(Object value,int max){String out=text(value).trim();if(out.length()>max)throw error("文本过长");return out;}
    private static String required(Object value,String name,int max){String out=optional(value,max);if(out.isEmpty())throw error("请填写"+name);return out;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> input,String field){Object value=input.get(field);if(!(value instanceof List))throw error("请填写分配明细");List<Map<String,Object>> out=new ArrayList<>();for(Object row:(List<?>)value){if(!(row instanceof Map))throw error("明细格式不正确");out.add((Map<String,Object>)row);}if(out.size()>1000)throw error("单次明细过多");return out;}
    private static Map<Long,Map<String,Object>> index(List<Map<String,Object>> rows,String field){Map<Long,Map<String,Object>> out=new LinkedHashMap<>();for(Map<String,Object> row:rows)out.put(id(row.get(field)),row);return out;}
    private static Map<String,Object> map(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put(String.valueOf(pairs[i]),pairs[i+1]);return out;}
    private static ServiceException error(String message){return new ServiceException(message);}
}
