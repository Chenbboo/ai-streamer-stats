package com.ruoyi.business.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectStaffAllocation;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import com.ruoyi.business.support.BusinessAllocationWeights;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded history repair: preview without writes, retain outside versions, use owner confirmations. */
@Service
public class BusinessHistoricalAllocationService {
    @Autowired private BusinessHistoricalAllocationMapper history;
    @Autowired private BusinessProjectMapper projects;
    @Autowired private BusinessAllocationRequestMapper requests;
    @Autowired private BusinessMemberDayCostMapper costs;
    @Autowired private BusinessProjectWorkMapper work;
    @Autowired private BusinessMemberDayCostService memberDays;
    @Autowired private BusinessCompanyAccessService access;
    @Autowired private ObjectMapper json;

    /** Launching a historical project must not silently invalidate later confirmed plans or frozen books. */
    public void validateInitialChange(Long projectId,Long userId,Date effectiveDate) {
        LocalDate from=dayDate(effectiveDate);if(!from.isBefore(today()))return;
        if(history.countFrozenResults(projectId,from.toString(),today().toString())>0)
            throw error("该历史期间已有冻结账目，不能直接调整初始投入，请通过调账流程处理");
        for(Map<String,Object> period:projects.selectUserAllocationTimeline(userId))
            if(projectId.equals(id(period.get("projectId")))&&date(period.get("effectiveFrom")).isAfter(from))
                throw error("该成员在 "+period.get("effectiveFrom")+" 已有后续投入记录，不能由初始投入覆盖，请按期间核对历史分配");
    }

    public Map<String,Object> members(Long projectId,Long actor) {
        BusinessProject p=target(projectId,actor);
        String start=day(p.getPlanStartDate()),end=today().toString();
        Map<Long,Map<String,Object>> members=new LinkedHashMap<>();
        for(Map<String,Object> row:history.selectMemberships(null,projectId,start,end))
            if(!"OBSERVER".equals(row.get("memberRole")))members.putIfAbsent(id(row.get("userId")),row);
        for(Map<String,Object> member:members.values()) {
            Long user=id(member.get("userId"));
            List<Map<String,Object>> periods=history.selectMemberships(user,projectId,start,end);
            Map<Long,Map<String,Object>> scope=new TreeMap<>();scope.put(projectId,member);
            List<Map<String,Object>> timeline=copyTimeline(projects.selectUserAllocationTimeline(user),scope);
            List<Map<String,Object>> roles=costs.selectRolePeriods(projectId);
            LocalDate gapStart=null,gapEnd=null;
            LocalDate first=date(start);if(first.isBefore(today().minusYears(10)))first=today().minusYears(10);
            for(LocalDate d=first;!d.isAfter(today());d=d.plusDays(1)) {
                final LocalDate current=d;
                boolean missing=periods.stream().anyMatch(m->participates(m,roles,user,current))
                    &&BusinessAllocationWeights.at(timeline,d).get(projectId)==null;
                if(missing){if(gapStart==null)gapStart=d;gapEnd=d;}
                else if(gapStart!=null)break;
            }
            if(gapStart!=null) {
                List<Map<String,Object>> all=history.selectMemberships(user,null,gapStart.toString(),gapEnd.toString());
                Map<Long,Map<String,Object>> allScopes=new TreeMap<>();Map<Long,List<Map<String,Object>>> allRoles=new TreeMap<>();
                for(Map<String,Object> m:all){Long pid=id(m.get("projectId"));allScopes.putIfAbsent(pid,m);allRoles.computeIfAbsent(pid,key->costs.selectRolePeriods(key));}
                List<Map<String,Object>> allTimeline=copyTimeline(projects.selectUserAllocationTimeline(user),allScopes);
                String firstState=null;
                for(LocalDate d=gapStart;!d.isAfter(gapEnd);d=d.plusDays(1)) {
                    Set<Long> participating=new TreeSet<>();
                    for(Map<String,Object> m:all)if(participates(m,allRoles.get(id(m.get("projectId"))),user,d))participating.add(id(m.get("projectId")));
                    Map<Long,Map<String,Object>> weights=BusinessAllocationWeights.at(allTimeline,d);Map<Long,String> state=new TreeMap<>();
                    for(Long pid:participating)state.put(pid,weightSignature(weights.get(pid)));
                    if(firstState==null)firstState=state.toString();
                    else if(!firstState.equals(state.toString())){gapEnd=d.minusDays(1);break;}
                }
            }
            member.put("gapFrom",gapStart==null?null:gapStart.toString());member.put("gapTo",gapEnd==null?null:gapEnd.toString());
        }
        return map("members",new ArrayList<>(members.values()),"projectStartDate",start,"today",end);
    }

    public Map<String,Object> workspace(Long projectId,Map<String,Object> body,Long actor) {
        return workspace(projectId,body,actor,false);
    }

    public Map<String,Object> scheduleWorkspace(Long projectId,Map<String,Object> body,Long actor) {
        return workspace(projectId,body,actor,true);
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> scheduleMembers(Long projectId,Long actor){
        BusinessProject p=target(projectId,actor);Map<String,Object> result=members(projectId,actor);
        Map<Long,Map<String,Object>> selected=new LinkedHashMap<>();
        for(Map<String,Object> m:(List<Map<String,Object>>)result.get("members"))selected.put(id(m.get("userId")),m);
        for(Map<String,Object> m:history.selectMemberships(null,projectId,day(p.getPlanStartDate()),"9999-12-31"))
            if(!"OBSERVER".equals(m.get("memberRole")))selected.putIfAbsent(id(m.get("userId")),m);
        result.put("members",new ArrayList<>(selected.values()));return result;
    }

    public Map<String,Object> scheduleState(Long projectId,Long user,Long actor){
        target(projectId,actor);
        if(user==null||history.selectMemberships(user,projectId,"1900-01-01","9999-12-31").isEmpty())throw error("该人员没有当前项目参与记录");
        List<Map<String,Object>> requestHistory=new ArrayList<>();
        for(Map<String,Object> req:requests.selectHistory(user))requestHistory.add(requestViewAny(req,actor));
        List<Map<String,Object>> records=projects.selectUserAllocationTimeline(user);
        return map("records",records,"periods",allocationPeriods(user,records),"history",requestHistory,
            "pendingRequest",requestViewAny(requests.selectPending(user),actor));
    }

    /** Read-only view of effective proportions, including participation gaps and automatic redistribution. */
    List<Map<String,Object>> allocationPeriods(Long user,List<Map<String,Object>> records){
        List<Map<String,Object>> memberships=history.selectMemberships(user,null,"1900-01-01","9999-12-31");
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();
        Map<Long,List<Map<String,Object>>> roles=new TreeMap<>();
        SortedSet<LocalDate> boundaries=new TreeSet<>();
        for(Map<String,Object> m:memberships){
            Long pid=id(m.get("projectId"));scopes.putIfAbsent(pid,m);
            roles.computeIfAbsent(pid,key->costs.selectRolePeriods(key));
            addBoundary(boundaries,effectiveStart(m),false);addBoundary(boundaries,m.get("projectEndDate"),true);
            addBoundary(boundaries,m.get("joinedDate"),false);addBoundary(boundaries,m.get("leftDate"),true);
        }
        for(List<Map<String,Object>> list:roles.values())for(Map<String,Object> role:list)
            addBoundary(boundaries,role.get("effectiveFrom"),false);
        List<Map<String,Object>> timeline=copyTimeline(records,scopes);
        for(Map<String,Object> r:timeline){
            addBoundary(boundaries,r.get("effectiveFrom"),false);addBoundary(boundaries,r.get("effectiveTo"),true);
            addBoundary(boundaries,r.get("projectStartDate"),false);addBoundary(boundaries,r.get("projectEndDate"),true);
        }
        List<LocalDate> dates=new ArrayList<>(boundaries);
        List<Map<String,Object>> result=new ArrayList<>();
        String previousSignature=null;
        for(int i=0;i<dates.size();i++){
            LocalDate from=dates.get(i),to=i+1<dates.size()?dates.get(i+1).minusDays(1):null;
            Set<Long> participating=new TreeSet<>();
            for(Map<String,Object> m:memberships)if(participates(m,roles.get(id(m.get("projectId"))),user,from))
                participating.add(id(m.get("projectId")));
            if(participating.isEmpty()){previousSignature=null;continue;}
            Map<Long,Map<String,Object>> weights=BusinessAllocationWeights.at(timeline,from);
            List<Map<String,Object>> rows=new ArrayList<>(),signature=new ArrayList<>();
            BigDecimal total=BigDecimal.ZERO;boolean incomplete=false;
            for(Long pid:participating){
                Map<String,Object> row=new LinkedHashMap<>(scopes.get(pid)),weight=weights.get(pid);
                boolean missing=weight==null,pending=weight!=null&&"PENDING".equals(weight.get("confirmationStatus"));
                BigDecimal value=missing||pending?null:number(weight.get("allocationValue"));
                String frozen=freezeReason(row,from,to);
                row.put("allocationValue",value);row.put("allocationMissing",missing);
                row.put("confirmationStatus",missing?null:weight.get("confirmationStatus"));
                row.put("autoRedistributed",weight!=null&&Boolean.TRUE.equals(weight.get("autoRedistributed")));
                row.put("freezeReason",frozen);row.put("frozen",frozen!=null);
                rows.add(row);incomplete|=missing||pending;
                if(value!=null)total=total.add(value);
                signature.add(map("projectId",pid,"value",value,"missing",missing,"pending",pending,
                    "auto",row.get("autoRedistributed"),"frozen",frozen));
            }
            String key=write(signature);
            if(key.equals(previousSignature)&&!result.isEmpty())result.get(result.size()-1).put("dateTo",to==null?null:to.toString());
            else result.add(map("dateFrom",from.toString(),"dateTo",to==null?null:to.toString(),"projects",rows,
                "totalPercent",total,"status",incomplete?"INCOMPLETE":total.compareTo(new BigDecimal("100"))==0?"FULL":total.compareTo(new BigDecimal("100"))>0?"OVER":"UNDER"));
            previousSignature=key;
        }
        return result;
    }

    private String freezeReason(Map<String,Object> row,LocalDate from,LocalDate to){
        if(!"0".equals(row.get("projectDelFlag")))return "DELETED";
        if(BusinessProjectLifecycle.isAccountingClosed(map("status",row.get("projectStatus"),
            "accountingState",row.get("accountingState"),"deliveryPolicyVersion",row.get("deliveryPolicyVersion"))))return "ACCOUNTING_CLOSED";
        return history.countFrozenResults(id(row.get("projectId")),from.toString(),to==null?"9999-12-31":to.toString())>0?"PERIOD_CLOSED":null;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> workspace(Long projectId,Map<String,Object> body,Long actor,boolean schedule) {
        target(projectId,actor);
        Long user=id(body.get("userId"));if(user==null)throw error(schedule?"请选择调整人员":"请选择补录人员");
        LocalDate from=date(body.get("dateFrom")),to=text(body.get("dateTo")).isEmpty()?null:date(body.get("dateTo"));
        if(!schedule)validateRange(from,to);
        else if(from.isBefore(today().minusYears(10))||from.isAfter(today().plusYears(10))||to!=null&&(to.isBefore(from)||to.isAfter(from.plusDays(3660))))
            throw error("请选择起止顺序正确的投入期间，每段最多10年");
        List<Map<String,Object>> memberships=history.selectMemberships(user,null,from.toString(),to==null?"9999-12-31":to.toString());
        if(schedule&&history.selectMemberships(user,projectId,"1900-01-01","9999-12-31").isEmpty())throw error("该人员没有当前项目参与记录");
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();
        Map<Long,List<Map<String,Object>>> roles=new TreeMap<>();
        for(Map<String,Object> m:memberships){Long pid=id(m.get("projectId"));scopes.putIfAbsent(pid,new LinkedHashMap<>(m));roles.computeIfAbsent(pid,key->costs.selectRolePeriods(key));}
        List<Map<String,Object>> raw=projects.selectUserAllocationTimeline(user);
        if(raw==null)raw=Collections.emptyList();
        List<Map<String,Object>> timeline=copyTimeline(raw,scopes);
        LocalDate requestedTo=to;
        if(schedule&&to==null)to=nextBoundary(from,memberships,roles,raw);
        LocalDate checkTo=to==null?(from.isAfter(today())?from:today()):to;
        if(ChronoUnit.DAYS.between(from,checkTo)>3660)throw error("每段投入期间最多10年，请分段设置");
        Set<Long> first=null;Map<Long,Map<String,Object>> firstWeights=null;
        List<String> gaps=new ArrayList<>();
        for(LocalDate d=from;!d.isAfter(checkTo);d=d.plusDays(1)){
            Set<Long> participating=new TreeSet<>();
            for(Map<String,Object> m:memberships)if(participates(m,roles.get(id(m.get("projectId"))),user,d))participating.add(id(m.get("projectId")));
            if(!schedule&&!participating.contains(projectId))throw error(d+"：该成员未参与当前项目，请按实际参与期间补录");
            if(participating.isEmpty())throw error(d+"：该人员没有参与中的计费项目，请核对实际参与日期");
            if(first==null)first=participating;
            else if(!first.equals(participating)){
                if(schedule)return map("splitDate",d.toString(),"message","同期参与项目发生变化，已按 "+d+" 拆分时间段");
                throw error("同期参与项目在 "+d+" 发生变化，请在该日期处分段补录");
            }
            Map<Long,Map<String,Object>> weights=BusinessAllocationWeights.at(timeline,d);
            if(firstWeights==null)firstWeights=weights;
            else for(Long pid:first){
                Map<String,Object> before=firstWeights.get(pid),now=weights.get(pid);
                if(!Objects.equals(weightSignature(before),weightSignature(now))){
                    if(schedule)return map("splitDate",d.toString(),"message","已有投入在 "+d+" 发生变化，已拆分并保留各段原比例");
                    throw error("投入记录在 "+d+" 发生变化，请分段补录，保留已有有效记录");
                }
            }
            if(weights.get(projectId)==null)gaps.add(d.toString());
            else if(!schedule)throw error(d+" 已有当前项目投入记录；补录只填缺口，已有比例请使用投入调整");
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Long pid:first){
            Map<String,Object> row=scopes.get(pid),weight=firstWeights.get(pid);
            row.put("allocationValue",weight==null?BigDecimal.ZERO:number(weight.get("allocationValue")));
            row.put("allocationMissing",weight==null);row.put("confirmationStatus",weight==null?null:weight.get("confirmationStatus"));
            String frozen=freezeReason(row,from,to);row.put("freezeReason",frozen);row.put("frozen",frozen!=null);
            row.put("startCorrectionDate",schedule&&!text(row.get("actualStartDate")).isEmpty()&&!from.isBefore(date(row.get("actualStartDate")))?null:startCorrection(row));
            row.put("rolePeriods",roles.get(pid));rows.add(row);
        }
        Map<String,Object> result=map("projectId",projectId,"userId",user,"dateFrom",from.toString(),"dateTo",to==null?null:to.toString(),
            "projects",rows,"gapDays",gaps.size(),"timeline",timeline);
        if(schedule){
            result.put("requestedDateTo",requestedTo==null?null:requestedTo.toString());
            result.put("preservedFrom",requestedTo==null&&to!=null?to.plusDays(1).toString():null);
            List<Map<String,Object>> records=new ArrayList<>();
            for(Map<String,Object> record:raw){Map<String,Object> copy=new LinkedHashMap<>(record);records.add(copy);}
            result.put("records",records);
            List<Map<String,Object>> requestHistory=new ArrayList<>();
            for(Map<String,Object> req:requests.selectHistory(user))requestHistory.add(requestViewAny(req,actor));
            result.put("history",requestHistory);
        }
        List<Object> versions=new ArrayList<>();versions.add(rows);versions.add(raw);versions.add(memberships);
        versions.add(work.selectBudgetRates(user,from.toString(),checkTo.toString()));versions.add(work.selectCalendars());versions.add(to==null?null:to.toString());versions.add(from.toString());
        result.put("versionToken",digest(versions));
        Map<String,Object> pending=requests.selectPending(user);
        result.put("pendingRequest",pending==null?null:requestViewAny(pending,actor));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> preview(Long projectId,Map<String,Object> input,Long actor) {
        return preview(projectId,input,actor,false);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> preview(Long projectId,Map<String,Object> input,Long actor,boolean schedule) {
        Map<String,Object> ws=workspace(projectId,input,actor,schedule);
        if(ws.containsKey("splitDate"))throw error(text(ws.get("message"))+"，请重新加载时间段");
        if(!Objects.equals(ws.get("versionToken"),input.get("versionToken")))throw error("历史参与记录、成本或投入已变化，请重新加载");
        List<Map<String,Object>> rows=(List<Map<String,Object>>)ws.get("projects");
        Map<Long,BigDecimal> submitted=parseAllocations(input.get("allocations"));
        Set<Long> required=new TreeSet<>();for(Map<String,Object> row:rows)required.add(id(row.get("projectId")));
        if(!required.equals(submitted.keySet()))throw error("请填写该期间参与的全部项目投入比例");
        BigDecimal total=submitted.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(new BigDecimal("100"))!=0)throw error("该期间全部项目投入比例合计必须为100%，当前为"+total+"%");
        LocalDate from=date(ws.get("dateFrom")),to=ws.get("dateTo")==null?null:date(ws.get("dateTo"));Long user=id(ws.get("userId"));
        LocalDate checkTo=to==null?(from.isAfter(today())?from:today()):to;
        List<Map<String,Object>> timeline=(List<Map<String,Object>>)ws.get("timeline");
        Set<Long> changed=new TreeSet<>();boolean needsConfirmation=false;
        for(Map<String,Object> row:rows){Long pid=id(row.get("projectId"));BigDecimal value=submitted.get(pid);
            boolean changing=Boolean.TRUE.equals(row.get("allocationMissing"))||number(row.get("allocationValue")).compareTo(value)!=0||row.get("startCorrectionDate")!=null;
            row.put("requestedValue",value);row.put("changed",changing);
            if(schedule&&"PENDING".equals(row.get("confirmationStatus")))throw error(row.get("projectName")+" 的原投入尚待确认，请先处理");
            if(!changing)continue;
            if(Boolean.TRUE.equals(row.get("frozen")))throw error(row.get("projectName")+
                ("DELETED".equals(row.get("freezeReason"))?" 已删除，历史投入不能在此修改，现有调账入口不支持该记录":
                 "ACCOUNTING_CLOSED".equals(row.get("freezeReason"))?" 已关账，历史成本需通过关账后调整处理":
                 " 该期间含已冻结账目，历史成本需通过调账流程处理"));
            if("PENDING".equals(row.get("confirmationStatus")))throw error(row.get("projectName")+" 的原投入尚待确认，请先处理");
            changed.add(pid);needsConfirmation|=!Objects.equals(id(row.get("ownerUserId")),actor);
        }
        List<Map<String,Object>> proposed=replaceTimeline(timeline,changed,submitted,from,to);
        for(LocalDate d=from;!d.isAfter(checkTo);d=d.plusDays(1)) {
            Map<Long,Map<String,Object>> effective=BusinessAllocationWeights.at(proposed,d);
            BigDecimal datedTotal=effective.values().stream().map(row->number(row.get("allocationValue"))).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(datedTotal.compareTo(new BigDecimal("100"))!=0)throw error(d+" 的历史跨项目投入合计不为100%，请核对期间内其他投入记录");
            for(Long pid:required)if(effective.get(pid)==null||number(effective.get(pid).get("allocationValue")).compareTo(submitted.get(pid))!=0)
                throw error(d+" 的历史投入发生自动分配变化，请在该日期处分段补录");
        }
        List<Map<String,Object>> impacts=new ArrayList<>();
        LocalDate estimateTo=to==null?from.withDayOfMonth(from.lengthOfMonth()):to;
        if(estimateTo.isBefore(today())&&to==null)estimateTo=today();
        if(ChronoUnit.DAYS.between(from,estimateTo)>3660)throw error("成本测算期间过长，请分段设置");
        for(Map<String,Object> row:rows){Long pid=id(row.get("projectId"));if(!changed.contains(pid)&&(!schedule||changed.isEmpty()||Boolean.TRUE.equals(row.get("frozen"))))continue;
            BusinessProject p=projects.selectProjectById(pid),simulated=new BusinessProject();BeanUtils.copyProperties(p,simulated);
            if(row.get("startCorrectionDate")!=null)simulated.setActualStartDate(java.sql.Date.valueOf(date(row.get("startCorrectionDate"))));
            BigDecimal before=BigDecimal.ZERO,projectBefore=BigDecimal.ZERO;int beforePending=0;
            for(Map<String,Object> c:costs.selectCosts(pid))if(within(c.get("bizDate"),from,estimateTo)){
                if(c.get("amount")!=null)projectBefore=projectBefore.add(number(c.get("amount")));
                if(user.equals(id(c.get("userId")))){if(c.get("amount")==null)beforePending++;else before=before.add(number(c.get("amount")));}
            }
            BigDecimal after=BigDecimal.ZERO,projectAfter=BigDecimal.ZERO;int working=0;Set<String> otherIssues=new LinkedHashSet<>();
            for(Map<String,Object> c:memberDays.previewHistoricalAllocation(simulated,from,estimateTo,user,proposed)) {
                if(c.get("amount")!=null)projectAfter=projectAfter.add(number(c.get("amount")));
                else if(!user.equals(id(c.get("userId"))))otherIssues.add(c.get("userName")+" / "+c.get("bizDate")+"："+c.get("issue"));
                if(user.equals(id(c.get("userId")))){
                    if(c.get("amount")==null)throw error(row.get("projectName")+" / "+c.get("userName")+" / "+c.get("bizDate")+"："+c.get("issue"));
                    working++;after=after.add(number(c.get("amount")));
                }
            }
            impacts.add(map("projectId",pid,"projectName",row.get("projectName"),"ownerName",row.get("ownerName"),
                "currency",row.get("currency"),"beforeAmount",before,"beforePending",beforePending,"afterAmount",after,
                "difference",after.subtract(before),"workingDays",working,"startCorrectionDate",row.get("startCorrectionDate"),
                "projectBeforeAmount",projectBefore,"projectAfterAmount",projectAfter,"otherIssues",new ArrayList<>(otherIssues),"estimateFrom",from.toString(),"estimateTo",estimateTo.toString()));
        }
        ws.remove("timeline");ws.put("allocations",input.get("allocations"));ws.put("impacts",impacts);
        ws.put("needsConfirmation",needsConfirmation);ws.put("kind",schedule?"SEGMENT":"HISTORY");ws.put("operatorUserId",actor);return ws;
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @SuppressWarnings("unchecked")
    public Map<String,Object> save(Long projectId,Map<String,Object> body,Long actor,String operator) {
        if(!Boolean.TRUE.equals(body.get("impactConfirmed")))throw error("请先测算并确认历史成本变化");
        String reason=text(body.get("reason"));if(reason.isEmpty()||reason.length()>500)throw error("请填写500字内的历史补录依据");
        Long user=id(body.get("userId"));requests.lockEmployee(user);
        if(requests.selectPending(user)!=null)throw error("该员工已有待确认投入申请，请先处理或撤回");
        lockProjects(projectId,body,actor);
        Map<String,Object> preview=preview(projectId,body,actor);preview.put("reason",reason);preview.put("impactConfirmed",true);
        Map<String,Object> req=map("userId",user,"userName",staffName(preview),"applicantId",actor,"applicantName",operator,
            "effectiveDate",java.sql.Date.valueOf(date(body.get("dateFrom"))),"reason",reason,"snapshotJson",write(preview));
        requests.insertRequest(req);Long requestId=id(req.get("requestId"));
        Set<Long> owners=new TreeSet<>();
        for(Map<String,Object> row:(List<Map<String,Object>>)preview.get("projects"))if(Boolean.TRUE.equals(row.get("changed"))){
            Long owner=id(row.get("ownerUserId"));if(owners.add(owner))requests.insertReview(map("requestId",requestId,"ownerUserId",owner,
                "ownerName",row.get("ownerName"),"projectId",row.get("projectId"),"status",owner.equals(actor)?"APPROVED":"PENDING"));
        }
        if(!Boolean.TRUE.equals(preview.get("needsConfirmation"))){apply(preview,operator);requests.finish(requestId,"APPLIED","历史投入已补齐并重新核算");}
        Map<String,Object> result=requestView(requests.selectRequest(requestId),actor);
        result.put("outcome",Boolean.TRUE.equals(preview.get("needsConfirmation"))?"PENDING":"APPLIED");return result;
    }

    /** One atomic request for all dated changes; unchanged source periods are never rewritten. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> previewSchedule(Long projectId,Map<String,Object> body,Long actor) {
        Object raw=body.get("segments");
        if(!(raw instanceof List)||((List<?>)raw).isEmpty()||((List<?>)raw).size()>20)throw error("请设置1至20个投入时间段");
        List<Map<String,Object>> inputs=new ArrayList<>();
        for(Object value:(List<?>)raw){if(!(value instanceof Map))throw error("投入时间段格式不正确");Map<String,Object> segment=new LinkedHashMap<>((Map<String,Object>)value);segment.put("userId",body.get("userId"));if(segment.containsKey("requestedDateTo"))segment.put("dateTo",segment.get("requestedDateTo"));inputs.add(segment);}
        inputs.sort(Comparator.comparing(s->date(s.get("dateFrom"))));
        List<Map<String,Object>> segments=new ArrayList<>();List<Map<String,Object>> allRows=new ArrayList<>();
        LocalDate lastEnd=null;boolean open=false,confirmation=false;Map<Long,Map<String,Object>> merged=new TreeMap<>();
        for(Map<String,Object> input:inputs){
            LocalDate from=date(input.get("dateFrom"));
            if(open||lastEnd!=null&&!from.isAfter(lastEnd))throw error("投入时间段不能重叠："+from);
            Map<String,Object> segment=preview(projectId,input,actor,true);
            lastEnd=segment.get("dateTo")==null?null:date(segment.get("dateTo"));open=lastEnd==null;
            confirmation|=Boolean.TRUE.equals(segment.get("needsConfirmation"));
            segment.remove("records");segment.remove("history");segment.remove("pendingRequest");
            segments.add(segment);
            for(Map<String,Object> row:(List<Map<String,Object>>)segment.get("projects")){
                allRows.add(row);Long pid=id(row.get("projectId"));
                if(!merged.containsKey(pid)||Boolean.TRUE.equals(row.get("changed")))merged.put(pid,row);
            }
        }
        // Validate dates between segments as well: retained records must cover them at 100%.
        LocalDate first=date(segments.get(0).get("dateFrom"));
        LocalDate end=open?(date(segments.get(segments.size()-1).get("dateFrom")).isAfter(today())?date(segments.get(segments.size()-1).get("dateFrom")):today()):lastEnd;
        if(ChronoUnit.DAYS.between(first,end)>3660)throw error("本次调整跨度最多10年，请分批设置");
        List<Map<String,Object>> combined=projects.selectUserAllocationTimeline(id(body.get("userId")));
        Map<Long,Map<String,Object>> scopes=new TreeMap<>();for(Map<String,Object> row:allRows)scopes.put(id(row.get("projectId")),row);
        combined=copyTimeline(combined,scopes);
        for(Map<String,Object> segment:segments){
            Map<Long,BigDecimal> values=parseAllocations(segment.get("allocations"));Set<Long> changed=new TreeSet<>();
            for(Map<String,Object> row:(List<Map<String,Object>>)segment.get("projects"))if(Boolean.TRUE.equals(row.get("changed")))changed.add(id(row.get("projectId")));
            combined=replaceTimeline(combined,changed,values,date(segment.get("dateFrom")),segment.get("dateTo")==null?null:date(segment.get("dateTo")));
        }
        List<Map<String,Object>> datedMemberships=history.selectMemberships(id(body.get("userId")),null,first.toString(),end.toString());
        Map<Long,List<Map<String,Object>>> datedRoles=new HashMap<>();
        for(Map<String,Object> m:datedMemberships)datedRoles.computeIfAbsent(id(m.get("projectId")),costs::selectRolePeriods);
        for(LocalDate d=first;!d.isAfter(end);d=d.plusDays(1)){
            Map<Long,Map<String,Object>> weights=BusinessAllocationWeights.at(combined,d);
            for(Map<String,Object> m:datedMemberships)if(participates(m,datedRoles.get(id(m.get("projectId"))),id(body.get("userId")),d)&&!weights.containsKey(id(m.get("projectId"))))
                throw error(d+" / "+m.get("projectName")+" 缺少投入记录，请补齐该时间段（没有投入也需明确设置0%）");
            BigDecimal total=weights.values().stream().map(r->number(r.get("allocationValue"))).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(total.compareTo(new BigDecimal("100"))!=0)throw error(d+" 的投入合计为 "+total+"%，请补齐时间段或核对保留记录");
        }
        Map<String,Object> result=map("kind","SEGMENTS","projectId",projectId,"userId",id(body.get("userId")),"dateFrom",first.toString(),
            "dateTo",open?null:lastEnd.toString(),"segments",segments,"projects",new ArrayList<>(merged.values()),"needsConfirmation",confirmation,"operatorUserId",actor);
        if(Boolean.TRUE.equals(body.get("initialAllocation"))){
            // A new project's other members and day ledgers are populated after its owner.
            // Verify the dated allocation inputs on confirmation, not those derived totals.
            List<Object> inputsForToken=new ArrayList<>();
            for(Map<String,Object> segment:segments)inputsForToken.add(map("dateFrom",segment.get("dateFrom"),"dateTo",segment.get("dateTo"),
                "versionToken",segment.get("versionToken"),"allocations",segment.get("allocations")));
            result.put("initialAllocation",true);result.put("previewToken",digest(inputsForToken));
        }else result.put("previewToken",digest(segments));
        return result;
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @SuppressWarnings("unchecked")
    public Map<String,Object> saveSchedule(Long projectId,Map<String,Object> body,Long actor,String operator) {
        if(!Boolean.TRUE.equals(body.get("impactConfirmed")))throw error("请先预览并确认比例与成本变化");
        String reason=text(body.get("reason"));if(reason.isEmpty()||reason.length()>500)throw error("请填写500字内的调整原因");
        Long user=id(body.get("userId"));requests.lockEmployee(user);
        if(requests.selectPending(user)!=null)throw error("该员工已有待确认投入申请，请先处理或撤回");
        lockSchedule(projectId,body,actor);
        Map<String,Object> snapshot=previewSchedule(projectId,body,actor);
        if(!Objects.equals(snapshot.get("previewToken"),body.get("previewToken")))throw error("成本或投入记录已变化，请重新预览并确认");
        snapshot.put("reason",reason);snapshot.put("impactConfirmed",true);
        Map<String,Object> req=map("userId",user,"userName",text(((List<Map<String,Object>>)snapshot.get("projects")).get(0).get("userName")),
            "applicantId",actor,"applicantName",operator,"effectiveDate",java.sql.Date.valueOf(date(snapshot.get("dateFrom"))),"reason",reason,"snapshotJson",write(snapshot));
        requests.insertRequest(req);Long requestId=id(req.get("requestId"));Set<Long> owners=new TreeSet<>();
        for(Map<String,Object> row:(List<Map<String,Object>>)snapshot.get("projects"))if(Boolean.TRUE.equals(row.get("changed"))){
            Long owner=id(row.get("ownerUserId"));if(owners.add(owner))requests.insertReview(map("requestId",requestId,"ownerUserId",owner,"ownerName",row.get("ownerName"),
                "projectId",row.get("projectId"),"status",owner.equals(actor)?"APPROVED":"PENDING"));
        }
        if(!Boolean.TRUE.equals(snapshot.get("needsConfirmation"))){applySchedule(snapshot,operator);requests.finish(requestId,"APPLIED","多段投入已保存并重新核算");}
        Map<String,Object> result=requestView(requests.selectRequest(requestId),actor);result.put("outcome",snapshot.get("needsConfirmation").equals(true)?"PENDING":"APPLIED");return result;
    }

    @SuppressWarnings("unchecked")
    private void lockSchedule(Long projectId,Map<String,Object> body,Long actor){
        Set<Long> ids=new TreeSet<>();ids.add(projectId);
        for(Map<String,Object> segment:(List<Map<String,Object>>)body.get("segments")){
            Map<String,Object> query=new LinkedHashMap<>(segment);query.put("userId",body.get("userId"));
            if(query.containsKey("requestedDateTo"))query.put("dateTo",query.get("requestedDateTo"));
            Map<String,Object> ws=scheduleWorkspace(projectId,query,actor);
            if(ws.containsKey("splitDate"))throw error(text(ws.get("message"))+"，请重新加载时间段");
            for(Map<String,Object> row:(List<Map<String,Object>>)ws.get("projects"))ids.add(id(row.get("projectId")));
        }
        for(Long pid:ids)projects.selectProjectByIdForUpdate(pid);
    }

    @SuppressWarnings("unchecked")
    private void applySchedule(Map<String,Object> snapshot,String operator){
        Set<Long> restored=new HashSet<>();
        for(Map<String,Object> segment:(List<Map<String,Object>>)snapshot.get("segments")){
            segment.put("reason",snapshot.get("reason"));segment.put("operatorUserId",snapshot.get("operatorUserId"));
            for(Map<String,Object> row:(List<Map<String,Object>>)segment.get("projects"))if(row.get("startCorrectionDate")!=null){
                if(!restored.add(id(row.get("projectId"))))row.put("startCorrectionDate",null);
            }
            apply(segment,operator);
        }
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> review(Long requestId,String decision,String comment,Long actor,String operator) {
        Map<String,Object> req=requests.selectRequest(requestId);if(req==null)throw error("历史投入申请不存在");
        requests.lockEmployee(id(req.get("userId")));req=requests.selectRequest(requestId);
        if(!"PENDING".equals(req.get("status")))throw error("该申请已处理，请刷新");
        if(!Arrays.asList("APPROVED","REJECTED","WITHDRAWN").contains(decision))throw error("请选择确认、退回或撤回");
        if(text(comment).length()>500)throw error("处理说明不能超过500字");
        Map<String,Object> view=requestView(req,actor);
        if("WITHDRAWN".equals(decision)){
            if(!Boolean.TRUE.equals(view.get("canWithdraw")))throw error("只有发起人可以撤回申请");
            requests.finish(requestId,decision,comment);
        }else{
            if(!Boolean.TRUE.equals(view.get("canReview")))throw error("只有本次待确认的负责人可以处理");
            if("REJECTED".equals(decision)){
                if(text(comment).isEmpty())throw error("退回时请填写原因");requests.review(requestId,actor,decision,comment);requests.finish(requestId,decision,comment);
            }else{
                Map<String,Object> snapshot=read(req);Long pid=id(snapshot.get("projectId")),applicant=id(req.get("applicantId"));
                Map<String,Object> current;
                boolean schedule="SEGMENTS".equals(snapshot.get("kind"));
                try{
                    if(schedule){
                        lockSchedule(pid,snapshot,applicant);current=previewSchedule(pid,snapshot,applicant);
                        if(!Objects.equals(snapshot.get("previewToken"),current.get("previewToken")))throw error("成本或投入记录已变化，请重新发起并确认");
                    }else{lockProjects(pid,snapshot,applicant);current=preview(pid,snapshot,applicant);}
                }catch(ServiceException changed){requests.finish(requestId,"INVALIDATED",changed.getMessage());return requestView(requests.selectRequest(requestId),actor);}
                // Repricing uses the fresh preview only if all dated inputs still match the reviewed snapshot.
                requests.review(requestId,actor,"APPROVED",comment);
                if(requests.selectReviews(requestId).stream().noneMatch(row->"PENDING".equals(row.get("status")))){
                    current.put("reason",snapshot.get("reason"));current.put("operatorUserId",actor);
                    if(schedule)applySchedule(current,operator);else apply(current,operator);
                    requests.finish(requestId,"APPLIED",schedule?"相关负责人已确认多段投入":"相关负责人已确认历史补录");
                }
            }
        }
        return requestView(requests.selectRequest(requestId),actor);
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String,Object> snapshot,String operator) {
        if("SEGMENT".equals(snapshot.get("kind"))&&((List<Map<String,Object>>)snapshot.get("projects")).stream().noneMatch(r->Boolean.TRUE.equals(r.get("changed"))))return;
        Long user=id(snapshot.get("userId"));LocalDate from=date(snapshot.get("dateFrom")),to=snapshot.get("dateTo")==null?null:date(snapshot.get("dateTo"));
        List<Map<String,Object>> timeline=new ArrayList<>(projects.selectUserAllocationTimeline(user));
        for(Map<String,Object> row:(List<Map<String,Object>>)snapshot.get("projects")){
            if(!Boolean.TRUE.equals(row.get("changed")))continue;Long pid=id(row.get("projectId"));
            if(row.get("startCorrectionDate")!=null&&history.restoreStart(pid,((Number)row.get("projectVersion")).intValue(),text(row.get("startCorrectionDate")),operator)!=1)throw error("项目版本已变化，请重新补录");
            for(Map<String,Object> period:timeline)if(pid.equals(id(period.get("projectId")))&&overlaps(period,from,to)){
                BusinessProjectStaffAllocation old=projects.selectProjectStaffAllocationById(id(period.get("allocationId")));
                if(history.voidVersion(old.getAllocationId(),old.getVersion(),operator)!=1)throw error("历史投入版本已变化，请重新加载");
                LocalDate begin=dayDate(old.getEffectiveFrom()),end=old.getEffectiveTo()==null?null:dayDate(old.getEffectiveTo());
                if(begin.isBefore(from))insertCopy(old,begin,from.minusDays(1),operator);
                if(to!=null&&(end==null||end.isAfter(to)))insertCopy(old,to.plusDays(1),end,operator);
            }
            BusinessProjectStaffAllocation allocation=new BusinessProjectStaffAllocation();allocation.setProjectId(pid);allocation.setUserId(user);
            allocation.setUserName(text(row.get("userName")));allocation.setAllocationMode("PERCENTAGE");allocation.setAllocationValue(number(row.get("requestedValue")));
            allocation.setEffectiveFrom(java.sql.Date.valueOf(from));allocation.setEffectiveTo(to==null?null:java.sql.Date.valueOf(to));allocation.setConfirmationStatus("CONFIRMED");
            boolean schedule="SEGMENT".equals(snapshot.get("kind"));
            allocation.setExceptionAllowed("0");allocation.setCreateBy(operator);allocation.setRemark((schedule?"分段投入：":"历史补录：")+snapshot.get("reason"));projects.insertProjectStaffAllocation(allocation);
            projects.insertEvent(map("projectId",pid,"eventType",schedule?"COST_ALLOCATION":"HISTORICAL_ALLOCATION","fromStatus",row.get("projectStatus"),"toStatus",row.get("projectStatus"),
                "operatorUserId",snapshot.get("operatorUserId"),"operatorName",operator,"reason",text(row.get("userName"))+" / "+from+" 至 "+to+" / "+row.get("allocationValue")+"% → "+row.get("requestedValue")+"% / "+snapshot.get("reason")));
        }
        // Rebuild every mutable participating project: rounding, payroll remainders and linked
        // daily accounting can change even where the member's numerical weight did not change.
        for(Map<String,Object> row:(List<Map<String,Object>>)snapshot.get("projects"))
            if(!Boolean.TRUE.equals(row.get("frozen")))memberDays.synchronizeHistoricalAllocationChange(id(row.get("projectId")),java.sql.Date.valueOf(from),to==null?null:java.sql.Date.valueOf(to),operator);
    }

    private void insertCopy(BusinessProjectStaffAllocation old,LocalDate from,LocalDate to,String operator){
        BusinessProjectStaffAllocation copy=new BusinessProjectStaffAllocation();BeanUtils.copyProperties(old,copy);copy.setAllocationId(null);
        copy.setEffectiveFrom(java.sql.Date.valueOf(from));copy.setEffectiveTo(to==null?null:java.sql.Date.valueOf(to));copy.setCreateBy(operator);projects.insertProjectStaffAllocation(copy);
    }

    @SuppressWarnings("unchecked")
    private void lockProjects(Long projectId,Map<String,Object> body,Long actor){
        Map<String,Object> ws=workspace(projectId,body,actor);
        for(Map<String,Object> row:(List<Map<String,Object>>)ws.get("projects"))projects.selectProjectByIdForUpdate(id(row.get("projectId")));
    }
    public boolean isHistoryRequest(Map<String,Object> request){return request!=null&&Arrays.asList("HISTORY","SEGMENTS").contains(read(request).get("kind"));}
    private Map<String,Object> requestViewAny(Map<String,Object> req,Long actor){
        Map<String,Object> view=requestView(req,actor);if(view!=null&&!isHistoryRequest(req))view.put("projects",read(req).get("projects"));return view;
    }
    public Map<String,Object> requestView(Map<String,Object> req,Long actor){
        if(req==null)return null;Map<String,Object> out=new LinkedHashMap<>(req),snapshot=read(req);out.remove("snapshotJson");
        out.put("kind",snapshot.get("kind"));out.put("dateTo",snapshot.get("dateTo"));out.put("projects",snapshot.get("projects"));out.put("impacts",snapshot.get("impacts"));out.put("segments",snapshot.get("segments"));
        List<Map<String,Object>> reviews=requests.selectReviews(id(req.get("requestId")));out.put("reviews",reviews);
        out.put("canWithdraw","PENDING".equals(req.get("status"))&&Objects.equals(id(req.get("applicantId")),actor));
        out.put("canReview","PENDING".equals(req.get("status"))&&reviews.stream().anyMatch(row->Objects.equals(id(row.get("ownerUserId")),actor)&&"PENDING".equals(row.get("status"))));return out;
    }
    private BusinessProject target(Long id,Long actor){BusinessProject p=projects.selectProjectById(id);
        if(p==null||!"0".equals(p.getDelFlag())||!BusinessMemberDayCostService.enabled(p))throw error("该项目未启用成员工作日成本或已删除");
        if(!Objects.equals(actor,p.getMainOwnerUserId())&&!access.project(p,actor))throw error("只有本项目负责人或获授权的公司管理人可以调整投入");
        BusinessProjectLifecycle.requireAccountingOpen(p);return p;
    }
    private boolean participates(Map<String,Object> m,List<Map<String,Object>> roles,Long user,LocalDate d){
        if(!withinBounds(m.get("joinedDate"),m.get("leftDate"),d)||!withinBounds(effectiveStart(m),m.get("projectEndDate"),d))return false;
        String role=text(m.get("memberRole"));for(Map<String,Object> r:roles)if(user.equals(id(r.get("userId")))&&!date(r.get("effectiveFrom")).isAfter(d))role=text(r.get("memberRole"));return !"OBSERVER".equals(role);
    }
    private String effectiveStart(Map<String,Object> row){String planned=text(row.get("planStartDate")),actual=text(row.get("actualStartDate"));
        boolean frozen="CLOSED".equals(row.get("accountingState"))||!"0".equals(row.get("projectDelFlag"))
            ||BusinessProjectLifecycle.isTerminal(text(row.get("projectStatus")))&&!"SEPARATED_V1".equals(row.get("deliveryPolicyVersion"));
        if(!frozen&&(actual.isEmpty()||actual.equals(text(row.get("recordedDate")))))return planned.isEmpty()?actual:planned;
        return planned.isEmpty()||actual.compareTo(planned)>0?actual:planned;
    }
    private String startCorrection(Map<String,Object> row){String start=effectiveStart(row),actual=text(row.get("actualStartDate"));return !start.isEmpty()&&!start.equals(actual)?start:null;}
    private List<Map<String,Object>> copyTimeline(List<Map<String,Object>> source,Map<Long,Map<String,Object>> scopes){List<Map<String,Object>> result=new ArrayList<>();if(source==null)return result;for(Map<String,Object> row:source){Map<String,Object> copy=new LinkedHashMap<>(row),scope=scopes.get(id(row.get("projectId")));if(scope!=null)copy.put("projectStartDate",effectiveStart(scope));result.add(copy);}return result;}
    static List<Map<String,Object>> replaceTimeline(List<Map<String,Object>> timeline,Set<Long> changed,Map<Long,BigDecimal> values,LocalDate from,LocalDate to){
        List<Map<String,Object>> result=new ArrayList<>();for(Map<String,Object> row:timeline){
            if(!changed.contains(id(row.get("projectId")))||!overlaps(row,from,to)){result.add(new LinkedHashMap<>(row));continue;}
            if(date(row.get("effectiveFrom")).isBefore(from)){Map<String,Object> left=new LinkedHashMap<>(row);left.put("effectiveTo",from.minusDays(1).toString());result.add(left);}
            if(to!=null&&(row.get("effectiveTo")==null||date(row.get("effectiveTo")).isAfter(to))){Map<String,Object> right=new LinkedHashMap<>(row);right.put("effectiveFrom",to.plusDays(1).toString());result.add(right);}
        }
        long simulatedId=-1;for(Map<String,Object> row:timeline)simulatedId=Math.min(simulatedId,id(row.get("allocationId"))-1);
        for(Long pid:changed)result.add(map("projectId",pid,"allocationId",simulatedId--,"allocationVersion",0,"allocationValue",values.get(pid),"effectiveFrom",from.toString(),"effectiveTo",to==null?null:to.toString(),"confirmationStatus","CONFIRMED"));return result;
    }
    static Map<Long,BigDecimal> parseAllocations(Object raw){if(!(raw instanceof List))throw error("请填写历史投入比例");Map<Long,BigDecimal> result=new TreeMap<>();for(Object item:(List<?>)raw){
        if(!(item instanceof Map))throw error("历史投入格式不正确");Map<?,?> row=(Map<?,?>)item;Long pid=id(row.get("projectId"));BigDecimal value=number(row.get("allocationValue"));
        if(pid==null||row.get("allocationValue")==null||value.signum()<0||value.compareTo(new BigDecimal("100"))>0||value.stripTrailingZeros().scale()>2||result.put(pid,value)!=null)throw error("每个项目投入须为0%至100%，最多两位小数，且不能重复");}return result;}
    private static String weightSignature(Map<String,Object> row){return row==null?"missing":text(row.get("allocationId"))+"|"+number(row.get("allocationValue"))+"|"+text(row.get("confirmationStatus"));}
    private static boolean overlaps(Map<String,Object> row,LocalDate from,LocalDate to){return (to==null||!date(row.get("effectiveFrom")).isAfter(to))&&(row.get("effectiveTo")==null||!date(row.get("effectiveTo")).isBefore(from));}
    private static boolean within(Object value,LocalDate from,LocalDate to){LocalDate d=date(value);return !d.isBefore(from)&&!d.isAfter(to);}
    private static boolean withinBounds(Object from,Object to,LocalDate day){return (from==null||text(from).isEmpty()||!day.isBefore(date(from)))&&(to==null||text(to).isEmpty()||!day.isAfter(date(to)));}
    private LocalDate nextBoundary(LocalDate from,List<Map<String,Object>> memberships,Map<Long,List<Map<String,Object>>> roles,List<Map<String,Object>> timeline){
        SortedSet<LocalDate> boundaries=new TreeSet<>();
        for(Map<String,Object> m:memberships){addBoundary(boundaries,m.get("joinedDate"),false);addBoundary(boundaries,m.get("leftDate"),true);addBoundary(boundaries,effectiveStart(m),false);addBoundary(boundaries,m.get("projectEndDate"),true);}
        for(List<Map<String,Object>> list:roles.values())for(Map<String,Object> r:list)addBoundary(boundaries,r.get("effectiveFrom"),false);
        for(Map<String,Object> r:timeline){addBoundary(boundaries,r.get("effectiveFrom"),false);addBoundary(boundaries,r.get("effectiveTo"),true);addBoundary(boundaries,r.get("projectEndDate"),true);}
        return boundaries.stream().filter(d->d.isAfter(from)).findFirst().map(d->d.minusDays(1)).orElse(null);
    }
    private static void addBoundary(Set<LocalDate> boundaries,Object value,boolean next){if(value!=null&&!text(value).isEmpty())boundaries.add(next?date(value).plusDays(1):date(value));}
    private static void validateRange(LocalDate from,LocalDate to){if(to==null||to.isBefore(from)||to.isAfter(today())||ChronoUnit.DAYS.between(from,to)>3660)throw error("请选择不晚于今天、起止顺序正确的历史期间（每次最多10年）");}
    private static LocalDate today(){return LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));}
    private static LocalDate date(Object value){try{return value instanceof Date?dayDate((Date)value):LocalDate.parse(text(value).substring(0,10));}catch(Exception ex){throw error("请填写有效的历史起止日期");}}
    private static LocalDate dayDate(Date value){return LocalDate.parse(day(value));}
    private static String day(Date value){return value==null?null:DateUtils.parseDateToStr("yyyy-MM-dd",value);}
    private static Long id(Object value){try{return value==null?null:Long.valueOf(text(value));}catch(Exception ex){throw error("人员或项目编号不正确");}}
    private static BigDecimal number(Object value){try{return value==null?BigDecimal.ZERO:new BigDecimal(text(value));}catch(Exception ex){throw error("投入或金额格式不正确");}}
    private static String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private String staffName(Map<String,Object> preview){for(Object value:(List<?>)preview.get("projects")){Map<?,?> row=(Map<?,?>)value;if(Objects.equals(id(row.get("projectId")),id(preview.get("projectId"))))return text(row.get("userName"));}return "";}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw error("历史投入快照无法保存");}}
    @SuppressWarnings("unchecked") private Map<String,Object> read(Map<String,Object> req){try{return json.readValue(text(req.get("snapshotJson")),Map.class);}catch(Exception ex){throw error("历史投入快照无法读取");}}
    // Display-only lock reasons must not invalidate existing owner confirmations.
    private Object tokenView(Object value){
        if(value instanceof Map){Map<Object,Object> copy=new LinkedHashMap<>();for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet())
            if(!"freezeReason".equals(entry.getKey()))copy.put(entry.getKey(),tokenView(entry.getValue()));return copy;}
        if(value instanceof List){List<Object> copy=new ArrayList<>();for(Object item:(List<?>)value)copy.add(tokenView(item));return copy;}
        return value;
    }
    private String digest(Object value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(write(tokenView(value)).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}catch(Exception ex){throw error("历史投入版本无法读取");}}
    static Map<String,Object> map(Object... values){Map<String,Object> row=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)row.put(String.valueOf(values[i]),values[i+1]);return row;}
    private static ServiceException error(String message){return new ServiceException(message);}
}
