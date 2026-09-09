package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

/** Personal allocation and payment evidence never generate or confirm cost facts. */
@Service
public class BusinessBonusDistributionService
{
    @Autowired private BusinessBonusDistributionMapper mapper;
    @Autowired private BusinessIncentiveMapper awards;
    @Autowired private BusinessProjectMapper projects;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public Map<String,Object> workspace(Long projectId,Long userId,boolean admin,boolean finance)
    {
        List<Map<String,Object>> directory=mapper.projects(userId,admin,finance);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("projects",directory);
        if(projectId==null && !directory.isEmpty()) projectId=num(directory.get(0).get("projectId"));
        if(projectId==null)return out;
        final Long selected=projectId;
        if(directory.stream().noneMatch(p->selected.equals(num(p.get("projectId")))))throw error("无权查看该项目奖金");
        BusinessProject p=project(projectId,false);
        boolean manager=admin||owner(p,userId)||sponsor(p,userId);
        boolean payer=sponsor(p,userId)||(finance&&mapper.companyAccess(projectId,userId)>0);
        boolean full=manager||payer;
        out.put("project",map("projectId",p.getProjectId(),"projectName",p.getProjectName(),"baseCurrency",p.getBaseCurrency()));
        out.put("manager",manager); out.put("personal",!full);
        out.put("canAllocate",owner(p,userId)); out.put("canPay",payer);
        out.put("recipients",owner(p,userId)?mapper.recipients(projectId):Collections.emptyList());
        List<BusinessIncentiveAward> awardList=awards.selectAwards(projectId);
        List<Map<String,Object>> available=new ArrayList<>();
        if(full)for(BusinessIncentiveAward a:awardList)if("APPROVED".equals(a.getStatus()))
            available.add(map("awardId",a.getAwardId(),"ruleName",a.getRuleName(),"score",a.getScoreSnapshot(),
                "currency",a.getCurrency(),"amount",a.getAmount(),"reserved",zero(mapper.reserved(a.getAwardId(),null)),
                "remaining",a.getAmount().subtract(zero(mapper.reserved(a.getAwardId(),null))),"costStatus",a.getCostStatus()));
        out.put("awards",available);
        List<Map<String,Object>> batches=new ArrayList<>();
        for(BusinessBonusAllocation b:mapper.allocations(projectId))
        {
            List<BusinessBonusAllocationLine> lines=mapper.lines(b.getAllocationId());
            if(!full)
            {
                if(!"APPROVED".equals(b.getStatus()))continue;
                lines=lines.stream().filter(l->userId.equals(l.getUserId())).collect(Collectors.toList());
                if(lines.isEmpty())continue;
            }
            BigDecimal total=ZERO,paid=ZERO;
            for(BusinessBonusAllocationLine l:lines)
            {
                l.setPaidAmount(zero(l.getPaidAmount())); total=total.add(l.getAmount());paid=paid.add(l.getPaidAmount());
                l.setPaymentStatus(l.getPaidAmount().signum()==0?"UNPAID":l.getPaidAmount().compareTo(l.getAmount())<0?"PARTIAL":"PAID");
            }
            final Set<Long> ids=lines.stream().map(BusinessBonusAllocationLine::getLineId).collect(Collectors.toSet());
            List<BusinessBonusPayment> payments=mapper.payments(b.getAllocationId()).stream().filter(v->ids.contains(v.getLineId())).collect(Collectors.toList());
            BusinessIncentiveAward a=awardList.stream().filter(v->v.getAwardId().equals(b.getAwardId())).findFirst().orElse(null);
            boolean editable=owner(p,userId)&&userId.equals(b.getCreatedUserId())&&Arrays.asList("DRAFT","RETURNED").contains(b.getStatus());
            batches.add(map("allocationId",b.getAllocationId(),"awardId",b.getAwardId(),"ruleName",a==null?"":a.getRuleName(),
                "currency",a==null?p.getBaseCurrency():a.getCurrency(),"score",a==null?null:a.getScoreSnapshot(),"status",b.getStatus(),
                "mode",b.getMode(),"reason",full?b.getReason():null,"version",b.getVersion(),"amount",total,"paidAmount",paid,
                "lines",lines,"payments",payments,"events",full?mapper.events(b.getAllocationId()):Collections.emptyList(),
                "canEdit",editable,"canReview",sponsor(p,userId)&&!userId.equals(b.getCreatedUserId())&&"SUBMITTED".equals(b.getStatus()),
                "canPay",payer&&"APPROVED".equals(b.getStatus())&&a!=null&&"APPROVED".equals(a.getStatus())&&"CONFIRMED".equals(a.getCostStatus())));
        }
        out.put("allocations",batches);return out;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BusinessBonusAllocation save(BusinessBonusAllocation input,Long userId,String userName)
    {
        if(input==null)throw error("请填写分配方案");
        BusinessIncentiveAward a=award(input.getAwardId());
        BusinessProject p=project(a.getProjectId(),true);
        a=awards.selectAwardForUpdate(input.getAwardId());requireApproved(a);requireOwner(p,userId);
        BusinessBonusAllocation previous=input.getAllocationId()==null?null:batch(input.getAllocationId());
        if(previous!=null)
        {
            if(!a.getAwardId().equals(previous.getAwardId())||!userId.equals(previous.getCreatedUserId()))throw error("只能修改本人创建的当前奖金分配");
            version(previous,input.getVersion());
            if(!Arrays.asList("DRAFT","RETURNED").contains(previous.getStatus()))throw error("只有草稿或退回的分配可以修改");
        }
        input.setProjectId(p.getProjectId());input.setReason(required(input.getReason(),"分配说明",500));
        input.setRequestKey(previous==null?required(input.getRequestKey(),"请求标识",64):previous.getRequestKey());
        validateLines(input,a,mapper.recipients(p.getProjectId()));
        if(previous==null)
        {
            BusinessBonusAllocation duplicate=mapper.byRequest(p.getProjectId(),input.getRequestKey());
            if(duplicate!=null)
            {
                duplicate.setLines(mapper.lines(duplicate.getAllocationId()));
                if(!userId.equals(duplicate.getCreatedUserId())||!Objects.equals(a.getAwardId(),duplicate.getAwardId())||!sameAllocation(input,duplicate))throw error("请求标识已用于不同分配，请刷新");
                return duplicate;
            }
        }
        if(zero(mapper.reserved(a.getAwardId(),input.getAllocationId())).add(input.getAmount()).compareTo(a.getAmount())>0)
            throw error("分配总额超过核准奖金的剩余可分配金额");
        input.setCreatedUserId(userId);input.setCreatedUserName(userName);
        if(previous==null){mapper.insertAllocation(input);input.setVersion(0);}
        else {if(mapper.updateAllocation(input)!=1)throw error("分配已更新，请刷新");mapper.deleteLines(input.getAllocationId());}
        for(BusinessBonusAllocationLine l:input.getLines()){l.setAllocationId(input.getAllocationId());mapper.insertLine(l);}
        event(input.getProjectId(),input.getAllocationId(),"SAVE",input.getReason(),input,userId,userName);
        return batch(input.getAllocationId());
    }

    void validateLines(BusinessBonusAllocation b,BusinessIncentiveAward award,List<Map<String,Object>> recipients)
    {
        if(!Arrays.asList("AMOUNT","PERCENT").contains(b.getMode()))throw error("请选择按金额或比例分配");
        if(b.getLines()==null||b.getLines().isEmpty()||b.getLines().size()>200)throw error("请填写 1 至 200 位领取人");
        Map<Long,String> names=new HashMap<>();
        for(Map<String,Object> r:recipients)names.put(num(r.get("userId")),String.valueOf(r.get("userName")));
        Set<Long> used=new HashSet<>();BigDecimal total=ZERO,percent=ZERO;
        for(BusinessBonusAllocationLine l:b.getLines())
        {
            if(l==null||!names.containsKey(l.getUserId())||!used.add(l.getUserId()))throw error("领取人须为项目成员，且不能重复");
            l.setUserName(names.get(l.getUserId()));l.setReason(required(l.getReason(),"个人分配说明",500));
            if("PERCENT".equals(b.getMode()))
            {
                money(l.getPercentage(),"分配比例");
                percent=percent.add(l.getPercentage());
                l.setAmount(award.getAmount().multiply(l.getPercentage()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP));
            }
            else l.setPercentage(null);
            money(l.getAmount(),"分配金额");total=total.add(l.getAmount());
        }
        if(percent.compareTo(new BigDecimal("100"))>0)throw error("比例合计不能超过 100%");
        b.setAmount(total);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BusinessBonusAllocation transition(Long id,Integer version,String action,String reason,Long userId,String userName)
    {
        BusinessBonusAllocation b=batch(id);BusinessProject p=project(b.getProjectId(),true);b=batch(id);
        version(b,version);reason=required(reason,"操作说明",500);
        String next;
        if("APPROVED".equals(action)||"RETURNED".equals(action))
        {
            if(!sponsor(p,userId)||userId.equals(b.getCreatedUserId()))throw error("须由项目归属老板核准，不能审核本人分配");
            if(!"SUBMITTED".equals(b.getStatus()))throw error("仅待核准分配可审核");
            requireApproved(award(b.getAwardId()));next=action;
        }
        else
        {
            requireOwner(p,userId);
            if(!userId.equals(b.getCreatedUserId()))throw error("只能处理本人创建的分配");
            if(!Arrays.asList("DRAFT","RETURNED").contains(b.getStatus()))throw error("仅草稿或退回分配可提交或撤销");
            if(!Arrays.asList("SUBMITTED","CANCELED").contains(action))throw error("操作不正确");
            if("SUBMITTED".equals(action))requireApproved(award(b.getAwardId()));
            next=action;
        }
        if(mapper.transition(id,version,next,userId,userName)!=1)throw error("分配已更新，请刷新");
        BusinessBonusAllocation updated=batch(id);updated.setLines(mapper.lines(id));
        event(p.getProjectId(),id,next,reason,updated,userId,userName);return updated;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BusinessBonusPayment pay(BusinessBonusPayment input,Long userId,String userName,boolean companyFinance)
    {
        if(input==null||input.getLineId()==null)throw error("请选择个人分配明细");
        BusinessBonusAllocationLine l=mapper.line(input.getLineId());
        if(l==null)throw error("分配明细不存在");
        BusinessBonusAllocation b=batch(l.getAllocationId());BusinessProject p=project(b.getProjectId(),true);
        if(!sponsor(p,userId)&&(!companyFinance||mapper.companyAccess(b.getProjectId(),userId)==0))throw error("仅项目归属老板或本公司获授权的经办人可登记发放");
        b=batch(b.getAllocationId());l=mapper.line(input.getLineId());
        BusinessIncentiveAward a=awards.selectAwardForUpdate(b.getAwardId());requireApproved(a);
        if(!"APPROVED".equals(b.getStatus()))throw error("分配核准后才能登记发放");
        if(!"CONFIRMED".equals(a.getCostStatus()))throw error("奖金成本确认入账后才能登记发放");
        money(input.getAmount(),"实付金额");
        input.setProjectId(b.getProjectId());input.setRequestKey(required(input.getRequestKey(),"请求标识",64));
        input.setReferenceNo(required(input.getReferenceNo(),"付款流水或收据编号",100));
        input.setReason(required(input.getReason(),"发放说明",500));
        input.setVoucher(required(input.getVoucher(),"付款凭证",1000));
        if(!input.getVoucher().startsWith("/profile/upload/")||input.getVoucher().contains("..")||input.getVoucher().contains("\\"))throw error("请上传有效付款凭证");
        if(!Arrays.asList("BANK","WECHAT","ALIPAY","CASH","OTHER").contains(input.getMethod()))throw error("请选择付款方式");
        if(input.getPaidDate()==null||day(input.getPaidDate()).compareTo(day(new Date()))>0
            ||b.getApprovedTime()==null||day(input.getPaidDate()).compareTo(day(b.getApprovedTime()))<0)throw error("实付日期须在分配核准日与今天之间");
        BusinessBonusPayment duplicate=mapper.paymentRequest(b.getProjectId(),input.getRequestKey());
        if(duplicate!=null){if(!samePayment(input,duplicate))throw error("请求标识已用于不同付款，请刷新");return duplicate;}
        if(mapper.paymentReference(l.getLineId(),input.getReferenceNo())!=null)throw error("该领取人的付款流水已登记");
        if(zero(l.getPaidAmount()).add(input.getAmount()).compareTo(l.getAmount())>0)throw error("实付金额超过该领取人的未发放金额");
        input.setRecordedUserId(userId);input.setRecordedUserName(userName);input.setStatus("RECORDED");
        mapper.insertPayment(input);event(b.getProjectId(),b.getAllocationId(),"PAYMENT",input.getReason(),input,userId,userName);
        return input;
    }

    private boolean sameAllocation(BusinessBonusAllocation a,BusinessBonusAllocation b)
    {
        if(!Objects.equals(a.getMode(),b.getMode())||!Objects.equals(a.getReason(),b.getReason())||a.getLines().size()!=b.getLines().size())return false;
        for(int i=0;i<a.getLines().size();i++){BusinessBonusAllocationLine x=a.getLines().get(i),y=b.getLines().get(i);
            if(!Objects.equals(x.getUserId(),y.getUserId())||x.getAmount().compareTo(y.getAmount())!=0||!Objects.equals(x.getReason(),y.getReason())
                ||(x.getPercentage()==null)!=(y.getPercentage()==null)||x.getPercentage()!=null&&x.getPercentage().compareTo(y.getPercentage())!=0)return false;}
        return true;
    }
    private boolean samePayment(BusinessBonusPayment a,BusinessBonusPayment b)
    {return Objects.equals(a.getLineId(),b.getLineId())&&a.getAmount().compareTo(b.getAmount())==0&&day(a.getPaidDate()).equals(day(b.getPaidDate()))
        &&Objects.equals(a.getMethod(),b.getMethod())&&Objects.equals(a.getReferenceNo(),b.getReferenceNo())&&Objects.equals(a.getVoucher(),b.getVoucher())&&Objects.equals(a.getReason(),b.getReason());}
    private void event(Long projectId,Long id,String type,String reason,Object snapshot,Long userId,String userName)
    {mapper.event(map("projectId",projectId,"allocationId",id,"eventType",type,"reason",reason,"snapshot",JSON.toJSONString(snapshot),"userId",userId,"userName",userName));}
    private BusinessProject project(Long id,boolean lock){BusinessProject p=lock?projects.selectProjectByIdForUpdate(id):projects.selectProjectById(id);if(p==null||!"0".equals(p.getDelFlag()))throw error("项目不存在");return p;}
    private BusinessIncentiveAward award(Long id){BusinessIncentiveAward a=id==null?null:awards.selectAward(id);if(a==null)throw error("奖金不存在");return a;}
    private BusinessBonusAllocation batch(Long id){BusinessBonusAllocation b=mapper.allocation(id);if(b==null)throw error("分配方案不存在");return b;}
    private void requireApproved(BusinessIncentiveAward a){if(!"APPROVED".equals(a.getStatus()))throw error("只能分配已核准奖金");}
    private boolean owner(BusinessProject p,Long u){return u!=null&&u.equals(p.getMainOwnerUserId());}
    private boolean sponsor(BusinessProject p,Long u){return u!=null&&u.equals(p.getSponsorOwnerUserId()==null?p.getInitiatorUserId():p.getSponsorOwnerUserId());}
    private void requireOwner(BusinessProject p,Long u){if(!owner(p,u))throw error("仅项目主负责人可分配奖金");}
    private void version(BusinessBonusAllocation b,Integer v){if(v==null||!v.equals(b.getVersion()))throw error("分配已更新，请刷新");}
    private static BigDecimal zero(BigDecimal x){return x==null?ZERO:x;}
    private static void money(BigDecimal x,String label){if(x==null||x.signum()<=0||x.scale()>2||x.compareTo(new BigDecimal("999999999999999999.99"))>0)throw error(label+"须大于零且最多两位小数");}
    private static String required(String x,String label,int max){if(x==null||x.trim().isEmpty()||x.trim().length()>max)throw error(label+"不能为空且最多 "+max+" 字");return x.trim();}
    private static String day(Date d){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd");f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return f.format(d);}
    private static Long num(Object x){return x==null?null:Long.valueOf(String.valueOf(x));}
    private static Map<String,Object> map(Object... entries){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)m.put((String)entries[i],entries[i+1]);return m;}
    private static ServiceException error(String text){return new ServiceException(text);}
}
