package com.ruoyi.business.service.impl;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessProjectManagementFeeMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.alibaba.fastjson2.JSON;

/** Project management fee configuration, accounting settlement and non-accounting payment evidence. */
@Service
public class BusinessProjectManagementFeeService
{
    private static final int ELIGIBILITY_PROJECT_COUNT=3;
    private static final List<String> MODES=Arrays.asList("FIXED","PROFIT_RATE","WAIVED");
    private static final List<String> PAYMENT_METHODS=Arrays.asList("BANK","WECHAT","ALIPAY","CASH","OTHER");
    @Autowired private BusinessProjectManagementFeeMapper mapper;
    @Autowired private BusinessProjectMapper projects;
    @Autowired private BusinessAccountingMapper accounting;

    public Map<String,Object> workspace(Long projectId,Long userId,boolean viewAll,boolean payer)
    {
        BusinessProject project=project(projectId,false);
        boolean sponsor=sponsor(project,userId),owner=userId!=null&&userId.equals(project.getMainOwnerUserId());
        boolean member=userId!=null&&accounting.selectAccountingMemberRole(projectId,userId)!=null;
        if(!viewAll&&!sponsor&&!owner&&!member)throw error("无权查看该项目管理费");
        return build(project,mapper.selectFee(projectId),userId,sponsor,payer);
    }

    /** Used by the project settlement status after that service has already enforced project access. */
    public Map<String,Object> settlementSnapshot(BusinessProject project,Long userId,boolean payer)
    {
        return build(project,mapper.selectFee(project.getProjectId()),userId,sponsor(project,userId),payer);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> configure(Long projectId,Map<String,Object> input,Long userId,String userName)
    {
        if(input==null)throw error("请填写项目管理费规则");
        BusinessProject project=project(projectId,true);
        if(!sponsor(project,userId))throw error("只有项目归属老板可以设置项目管理费");
        if("CLOSED".equals(accountingState(project)))throw error("项目核算已关闭，不能修改管理费");
        Map<String,Object> current=mapper.selectFeeForUpdate(projectId);
        if(current!=null&&"SETTLED".equals(current.get("status")))throw error("项目管理费已确认入账，不能修改");
        if(current!=null&&!Objects.equals(integer(input.get("version")),integer(current.get("version"))))throw error("管理费配置已更新，请刷新后重试");
        Map<String,Object> eligibility=ownerLoad(project);
        boolean sameRecipient=current!=null&&Objects.equals(longValue(current.get("recipientUserId")),project.getMainOwnerUserId());
        Integer currentSnapshotCount=current==null?null:integer(current.get("eligibilityProjectCount"));
        boolean qualifiedSnapshot=sameRecipient&&currentSnapshotCount!=null&&currentSnapshotCount>=ELIGIBILITY_PROJECT_COUNT;
        if(!Boolean.TRUE.equals(eligibility.get("eligible"))&&!qualifiedSnapshot)
            throw error("负责人当前在管项目"+eligibility.get("projectCount")+"个，达到"+ELIGIBILITY_PROJECT_COUNT+"个后才能设置管理费");
        String mode=upper(input.get("calculationMode"));
        if(!MODES.contains(mode))throw error("请选择管理费计算方式");
        String reason=required(input.get("configReason"),"设置说明",500);
        BigDecimal fixed=null,rate=null;
        if("FIXED".equals(mode))fixed=positiveMoney(input.get("fixedAmount"),"固定管理费");
        if("PROFIT_RATE".equals(mode))
        {
            rate=decimal(input.get("profitRate"));
            if(rate==null||rate.signum()<=0||rate.compareTo(new BigDecimal("100"))>0||rate.scale()>4)
                throw error("利润提取比例须大于0、不超过100%，且最多四位小数");
        }
        Map<String,Object> values=map("projectId",projectId,"recipientUserId",project.getMainOwnerUserId(),
            "recipientUserName",project.getMainOwnerName(),"calculationMode",mode,"fixedAmount",fixed,
            "profitRate",rate,"currency",project.getBaseCurrency(),
            "status","WAIVED".equals(mode)?"WAIVED":"CONFIGURED","configReason",reason,"userId",userId,"userName",userName,
            "eligibilityProjectCount",Boolean.TRUE.equals(eligibility.get("eligible"))?eligibility.get("projectCount"):current.get("eligibilityProjectCount"),
            "eligibilityProjectIds",Boolean.TRUE.equals(eligibility.get("eligible"))?eligibility.get("projectIds"):current.get("eligibilityProjectIds"),
            "eligibilityProjectNames",Boolean.TRUE.equals(eligibility.get("eligible"))?eligibility.get("projectNames"):current.get("eligibilityProjectNames"));
        mapper.saveConfiguration(values);
        Map<String,Object> saved=mapper.selectFee(projectId);
        event(projectId,saved.get("feeId"),"CONFIGURED",reason,saved,userId,userName);
        return build(project,saved,userId,true,false);
    }

    /** Called in the same transaction immediately before the accounting close. */
    public Map<String,Object> settle(Long projectId,Date closeDate,Long userId,String userName)
    {
        BusinessProject project=project(projectId,true);
        if(!sponsor(project,userId))throw error("只有项目归属老板可以确认项目管理费");
        Map<String,Object> fee=mapper.selectFeeForUpdate(projectId);
        Map<String,Object> eligibility=ownerLoad(project);
        if(fee!=null&&!Objects.equals(longValue(fee.get("recipientUserId")),project.getMainOwnerUserId()))fee=null;
        if(fee==null&&!Boolean.TRUE.equals(eligibility.get("eligible")))
        {
            Map<String,Object> skipped=new LinkedHashMap<String,Object>(eligibility);
            skipped.put("projectId",projectId);skipped.put("settledAmount",ZERO.setScale(2));skipped.put("status","INELIGIBLE");
            event(projectId,null,"INELIGIBLE","负责人未达到3个在管项目，项目不计管理费",skipped,userId,userName);
            return skipped;
        }
        if(fee==null)throw error("负责人已达到管理费条件，请先为本项目设置管理费或明确不发放");
        if("SETTLED".equals(fee.get("status")))return fee;
        Map<String,Object> basis=basis(projectId);
        BigDecimal amount=calculate(fee,basis);
        Long factId=null;
        if(amount.signum()>0)
        {
            String key="PROJECT-MANAGEMENT-FEE-"+projectId;
            BusinessOperatingFact existing=accounting.selectFactByIdempotencyKey(key);
            if(existing!=null)throw error("项目管理费成本已存在，请刷新核对");
            Map<String,Object> category=accounting.selectCategoryByCode("PROJECT_MANAGEMENT_FEE");
            if(category==null)throw error("项目管理费成本类别尚未初始化");
            BusinessOperatingFact fact=new BusinessOperatingFact();
            fact.setProjectId(projectId);fact.setCompanyDeptId(project.getCompanyDeptId());fact.setBizDate(closeDate);
            fact.setCategoryId(longValue(category.get("categoryId")));fact.setCategoryCode("PROJECT_MANAGEMENT_FEE");
            fact.setCategoryName(String.valueOf(category.get("categoryName")));fact.setFactKind("COST");
            fact.setAmount(amount);fact.setCurrency(project.getBaseCurrency());
            fact.setDescription("项目负责人管理费："+project.getMainOwnerName());fact.setCounterparty(project.getMainOwnerName());
            fact.setSourceDomain("PROJECT");fact.setSourceType("MANAGEMENT_FEE");fact.setSourceId(String.valueOf(projectId));
            fact.setSourceLineKey("FINAL");fact.setStatus("CONFIRMED");fact.setIdempotencyKey(key);
            fact.setConfirmedUserId(userId);fact.setConfirmedUserName(userName);fact.setConfirmedTime(new Date());
            fact.setCreateUserId(userId);fact.setCreateBy(userName);fact.setRemark("核算关闭时一次性确认；后续付款不重复入账");
            accounting.insertFact(fact);factId=fact.getFactId();
        }
        Map<String,Object> update=new LinkedHashMap<String,Object>(basis);
        update.put("projectId",projectId);update.put("settledAmount",amount);update.put("accountingFactId",factId);
        update.put("userId",userId);update.put("userName",userName);
        if(mapper.settle(update)!=1)throw error("管理费配置已更新，请刷新后重试");
        Map<String,Object> settled=mapper.selectFee(projectId);
        event(projectId,settled.get("feeId"),"SETTLED","核算关闭时确认项目管理费",settled,userId,userName);
        return settled;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> pay(Long projectId,Map<String,Object> input,Long userId,String userName)
    {
        if(input==null)throw error("请填写项目管理费付款信息");
        BusinessProject project=project(projectId,true);
        if(!sponsor(project,userId))throw error("只有项目归属老板可以登记项目管理费付款");
        if(!"CLOSED".equals(accountingState(project)))throw error("项目核算关闭后才能登记管理费付款");
        Map<String,Object> fee=mapper.selectFeeForUpdate(projectId);
        if(fee==null||!"SETTLED".equals(fee.get("status")))throw error("项目管理费尚未结算");
        BigDecimal total=decimal(fee.get("settledAmount"));
        BigDecimal amount=positiveMoney(input.get("amount"),"实付金额");
        String requestKey=required(input.get("requestKey"),"请求标识",64);
        Map<String,Object> duplicate=mapper.selectPaymentByRequest(projectId,requestKey);
        String reference=required(input.get("referenceNo"),"付款流水或收据编号",100);
        if(duplicate!=null)
        {
            if(!Objects.equals(longValue(fee.get("feeId")),longValue(duplicate.get("feeId")))
                ||amount.compareTo(decimal(duplicate.get("amount")))!=0||!reference.equals(duplicate.get("referenceNo")))
                throw error("请求标识已用于不同付款，请刷新后重试");
            return duplicate;
        }
        BigDecimal paid=paymentTotal(longValue(fee.get("feeId")));
        if(paid.add(amount).compareTo(total)>0)throw error("实付金额超过尚未结清金额");
        if(mapper.countPaymentReference(longValue(fee.get("feeId")),reference)>0)throw error("该付款流水已经登记");
        String method=upper(input.get("method"));if(!PAYMENT_METHODS.contains(method))throw error("请选择付款方式");
        String voucher=required(input.get("voucher"),"付款凭证",1000);
        for(String path:voucher.split(","))if(!path.trim().startsWith("/profile/upload/")||path.contains("..")||path.contains("\\"))throw error("请上传有效付款凭证");
        Date paidDate=DateUtils.parseDate(input.get("paidDate"));
        if(paidDate==null||day(paidDate).compareTo(day(new Date()))>0)throw error("实付日期不能晚于今天");
        Date settled=dateValue(fee.get("settledTime"));
        if(settled!=null&&day(paidDate).compareTo(day(settled))<0)throw error("实付日期不能早于管理费结算日");
        Map<String,Object> row=map("feeId",fee.get("feeId"),"projectId",projectId,"amount",amount,"paidDate",paidDate,
            "method",method,"referenceNo",reference,"voucher",voucher,"reason",required(input.get("reason"),"付款说明",500),
            "requestKey",requestKey,"userId",userId,"userName",userName);
        mapper.insertPayment(row);event(projectId,fee.get("feeId"),"PAYMENT",String.valueOf(row.get("reason")),row,userId,userName);return row;
    }

    private Map<String,Object> build(BusinessProject project,Map<String,Object> stored,Long userId,boolean sponsor,boolean payer)
    {
        Map<String,Object> basis=basis(project.getProjectId());
        Map<String,Object> eligibility=ownerLoad(project);
        boolean sameRecipient=stored!=null&&Objects.equals(longValue(stored.get("recipientUserId")),project.getMainOwnerUserId());
        Map<String,Object> effective=sameRecipient?stored:null;
        Map<String,Object> out=effective==null?new LinkedHashMap<String,Object>():new LinkedHashMap<String,Object>(effective);
        if(effective==null&&stored!=null)out.put("version",stored.get("version"));
        out.putAll(basis);out.put("projectId",project.getProjectId());out.put("projectName",project.getProjectName());
        out.put("recipientUserId",effective==null?project.getMainOwnerUserId():effective.get("recipientUserId"));
        out.put("recipientUserName",effective==null?project.getMainOwnerName():effective.get("recipientUserName"));
        out.put("currency",effective==null?project.getBaseCurrency():effective.get("currency"));
        out.putAll(eligibility);
        Integer snapshotCount=effective==null?null:integer(effective.get("eligibilityProjectCount"));
        boolean qualifiedSnapshot=snapshotCount!=null&&snapshotCount>=ELIGIBILITY_PROJECT_COUNT;
        boolean eligible=Boolean.TRUE.equals(eligibility.get("eligible"));
        out.put("configured",effective!=null);out.put("configurationRequired",eligible&&effective==null);
        out.put("ownerChanged",stored!=null&&!sameRecipient);
        out.put("canConfigure",sponsor&&!"CLOSED".equals(accountingState(project))
            &&(eligible||qualifiedSnapshot)&&(effective==null||!"SETTLED".equals(effective.get("status"))));
        BigDecimal estimate=effective==null?ZERO:calculate(effective,basis);out.put("estimatedAmount",estimate);
        Long feeId=effective==null?null:longValue(effective.get("feeId"));
        List<Map<String,Object>> payments=feeId==null?java.util.Collections.<Map<String,Object>>emptyList():mapper.selectPayments(feeId);
        BigDecimal paid=ZERO;for(Map<String,Object> payment:payments)paid=paid.add(decimal(payment.get("amount")));
        BigDecimal payable=effective!=null&&"SETTLED".equals(effective.get("status"))?decimal(effective.get("settledAmount")):ZERO;
        BigDecimal remaining=payable.subtract(paid).max(ZERO);
        String processStatus;
        if(effective==null)processStatus=eligible?"PENDING_CONFIG":"INELIGIBLE";
        else if("WAIVED".equals(effective.get("status"))||"WAIVED".equals(effective.get("calculationMode")))processStatus="WAIVED";
        else if(!"SETTLED".equals(effective.get("status")))processStatus=("CLOSED".equals(project.getStatus())||"CANCELED".equals(project.getStatus()))?"PENDING_SETTLEMENT":"ESTIMATED";
        else if(payable.signum()==0)processStatus="PAID";
        else if(paid.signum()==0)processStatus="UNPAID";
        else if(remaining.signum()>0)processStatus="PARTIAL";else processStatus="PAID";
        out.put("processStatus",processStatus);out.put("paidAmount",paid);out.put("remainingAmount",remaining);
        out.put("payments",payments);out.put("canPay",payer&&sponsor&&"SETTLED".equals(effective==null?null:effective.get("status"))&&remaining.signum()>0);
        return out;
    }

    private Map<String,Object> ownerLoad(BusinessProject project)
    {
        Long sponsorUserId=project.getSponsorOwnerUserId()==null?project.getInitiatorUserId():project.getSponsorOwnerUserId();
        List<Map<String,Object>> rows=projects.selectBossOwnerActiveProjects(sponsorUserId,false,project.getMainOwnerUserId());
        if(rows==null)rows=java.util.Collections.emptyList();
        StringBuilder ids=new StringBuilder(),names=new StringBuilder();
        for(Map<String,Object> row:rows)
        {
            if(ids.length()>0){ids.append(',');names.append('、');}
            ids.append(row.get("projectId"));names.append(row.get("projectName"));
        }
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("projectCount",rows.size());result.put("eligibilityThreshold",ELIGIBILITY_PROJECT_COUNT);
        result.put("eligible",rows.size()>=ELIGIBILITY_PROJECT_COUNT);result.put("ownerProjects",rows);
        result.put("projectIds",ids.toString());result.put("projectNames",names.toString());return result;
    }

    private Map<String,Object> basis(Long projectId)
    {
        Map<String,Object> raw=mapper.selectLifetimeBasis(projectId);Map<String,Object> out=new LinkedHashMap<String,Object>();
        BigDecimal revenue=decimal(raw.get("basisRevenue")),business=decimal(raw.get("basisBusinessCost"));
        BigDecimal personnel=decimal(raw.get("basisPersonnelCost")),bonus=decimal(raw.get("basisBonusCost")),adjustment=decimal(raw.get("basisAdjustment"));
        out.put("basisRevenue",revenue);out.put("basisBusinessCost",business);out.put("basisPersonnelCost",personnel);
        out.put("basisBonusCost",bonus);out.put("basisAdjustment",adjustment);
        out.put("preFeeProfit",revenue.subtract(business).subtract(personnel).subtract(bonus).add(adjustment));return out;
    }
    private BigDecimal calculate(Map<String,Object> fee,Map<String,Object> basis)
    {
        if(fee==null||"WAIVED".equals(fee.get("status"))||"WAIVED".equals(fee.get("calculationMode")))return ZERO.setScale(2);
        BigDecimal pre=decimal(basis.get("preFeeProfit")),amount;
        if("FIXED".equals(fee.get("calculationMode")))amount=decimal(fee.get("fixedAmount"));
        else amount=pre.max(ZERO).multiply(decimal(fee.get("profitRate"))).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        return amount.setScale(2,RoundingMode.HALF_UP);
    }
    private BigDecimal paymentTotal(Long feeId){BigDecimal total=ZERO;for(Map<String,Object> row:mapper.selectPayments(feeId))total=total.add(decimal(row.get("amount")));return total;}
    private void event(Long projectId,Object feeId,String type,String reason,Object snapshot,Long userId,String userName)
    {mapper.insertEvent(map("projectId",projectId,"feeId",feeId,"eventType",type,"reason",reason,"snapshot",JSON.toJSONString(snapshot),"userId",userId,"userName",userName));}
    private BusinessProject project(Long id,boolean lock){BusinessProject p=lock?projects.selectProjectByIdForUpdate(id):projects.selectProjectById(id);if(p==null||!"0".equals(p.getDelFlag()))throw error("项目不存在");return p;}
    private boolean sponsor(BusinessProject p,Long u){Long sponsor=p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId();return u!=null&&u.equals(sponsor);}
    private String accountingState(BusinessProject p){return p.getAccountingState()==null?"OPEN":p.getAccountingState();}
    private static BigDecimal positiveMoney(Object x,String label){BigDecimal v=decimalNullable(x);if(v==null||v.signum()<=0||v.scale()>2)throw error(label+"须大于0且最多两位小数");return v;}
    private static BigDecimal decimal(Object x){BigDecimal v=decimalNullable(x);return v==null?ZERO:v;}
    private static BigDecimal decimalNullable(Object x){return x==null?null:new BigDecimal(String.valueOf(x));}
    private static Integer integer(Object x){return x==null?null:Integer.valueOf(String.valueOf(x));}
    private static Long longValue(Object x){return x==null?null:Long.valueOf(String.valueOf(x));}
    private static Date dateValue(Object x){return x instanceof Date?(Date)x:DateUtils.parseDate(x);}
    private static String upper(Object x){return x==null?null:String.valueOf(x).trim().toUpperCase();}
    private static String required(Object x,String label,int max){String v=x==null?null:String.valueOf(x).trim();if(v==null||v.isEmpty()||v.length()>max)throw error("请填写"+label+"，且不超过"+max+"个字符");return v;}
    private static String day(Date d){return new SimpleDateFormat("yyyy-MM-dd").format(d);}
    private static Map<String,Object> map(Object... x){Map<String,Object> m=new LinkedHashMap<String,Object>();for(int i=0;i<x.length;i+=2)m.put((String)x[i],x[i+1]);return m;}
    private static ServiceException error(String text){return new ServiceException(text);}
}
