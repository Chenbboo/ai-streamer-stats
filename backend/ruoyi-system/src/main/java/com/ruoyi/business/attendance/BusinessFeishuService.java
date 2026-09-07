package com.ruoyi.business.attendance;

import static com.ruoyi.business.attendance.FeishuAttendanceClient.map;
import static com.ruoyi.business.attendance.FeishuAttendanceClient.sha256;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.mapper.BusinessFeishuMapper;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;

/** Company-scoped read-only Feishu integration and explicit authority cutover. */
@Service
public class BusinessFeishuService
{
    private static final List<String> CASES = Arrays.asList("NORMAL", "LEAVE", "CORRECTION", "CROSS_DAY", "UNMAPPED", "RECOVERY");
    private final BusinessFeishuMapper mapper;
    private final AttendanceProvider provider;
    private final TransactionTemplate transaction;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired @Qualifier("threadPoolTaskExecutor") private Executor executor;

    public BusinessFeishuService(BusinessFeishuMapper mapper, AttendanceProvider provider, PlatformTransactionManager manager)
    { this.mapper = mapper; this.provider = provider; this.transaction = new TransactionTemplate(manager); }

    public Map<String, Object> connections()
    {
        List<Map<String, Object>> rows = mapper.connections();
        for (Map<String, Object> c : rows)
        {
            c.put("configured", provider.isConfigured(string(c, "tenantKey")));
            c.put("openIssueCount", mapper.openIssues(id(c, "connectionId")));
            List<Map<String,Object>> history=mapper.runs(id(c,"connectionId"));
            String latest=history.isEmpty()?"NEVER":string(history.get(0),"status");
            c.put("lastSyncStatus",latest);
            c.put("quality", ("FAILED".equals(latest)||"PARTIAL".equals(latest)) ? "PARTIAL" : recent(c.get("lastCompleteAt")) ? "KNOWN" : c.get("lastCompleteAt") == null ? "UNKNOWN" : "STALE");
        }
        return map("configuration", provider.configurationStatus(), "companies", mapper.companies(), "connections", rows);
    }

    public Map<String,Object> connections(Long actor,boolean integrationManager)
    {
        if(integrationManager)
        {
            Map<String,Object> result=connections();
            for(Map<String,Object> c:(List<Map<String,Object>>)result.get("connections"))
            { Map<String,Object> company=mapper.company(id(c,"companyDeptId"));c.put("canCutoverOwner",company!=null&&Objects.equals(actor,id(company,"leaderUserId"))); }
            return result;
        }
        List<Map<String,Object>> companies=new ArrayList<>(),connections=new ArrayList<>();
        for(Map<String,Object> company:mapper.companies())
        {
            Map<String,Object> full=mapper.company(id(company,"companyDeptId"));
            if(full==null||!Objects.equals(actor,id(full,"leaderUserId")))continue;
            companies.add(company);Map<String,Object> c=mapper.connectionForCompany(id(company,"companyDeptId"));
            if(c!=null){c.put("companyName",company.get("companyName"));c.put("configured",provider.isConfigured(string(c,"tenantKey")));c.put("canCutoverOwner",true);c.remove("tenantKey");connections.add(c);}
        }
        return map("configuration",null,"companies",companies,"connections",connections);
    }
    public void requireConnectionOwner(Long connectionId,Long actor)
    { requireCompanyLeader(id(connection(connectionId),"companyDeptId"),actor); }
    public List<Map<String,Object>> readerCandidates(Long connectionId,Long actor)
    { Map<String,Object> c=connection(connectionId);requireCompanyLeader(id(c,"companyDeptId"),actor);return mapper.people(id(c,"companyDeptId")); }

    @Transactional
    public Map<String, Object> createConnection(Map<String, Object> input, Long actor)
    {
        Long companyId = requiredId(input, "companyDeptId");
        if (mapper.company(companyId) == null) fail("公司不存在或已停用");
        mapper.lockCompany(companyId);
        if (mapper.connectionForCompany(companyId) != null) fail("该公司已有连接；不可创建第二个假勤权威来源");
        String zone = requiredText(input, "timezone", 64); ZoneId.of(zone);
        String tenant = requiredText(input, "tenantKey", 128);
        if (!tenant.matches("[A-Za-z0-9_-]+")) fail("企业标识格式不正确");
        Map<String, Object> row = map("companyDeptId", companyId, "tenantKey", tenant, "timezone", zone, "actorId", actor);
        mapper.ensureTenant(tenant); mapper.insertConnection(row); audit(id(row,"connectionId"), "CREATE_CONNECTION", actor, null, "创建并行核对连接，未切换本地流程");
        return mapper.connection(id(row, "connectionId"));
    }

    public List<Map<String, Object>> mappings(Long connectionId) { connection(connectionId); return mapper.mappings(connectionId); }
    public List<Map<String, Object>> people(Long connectionId) { return mapper.people(id(connection(connectionId),"companyDeptId")); }
    public Map<String,Object> queryOptions(Long actor,boolean organizationReader)
    {
        List<Map<String,Object>> companies=new ArrayList<>();
        if(organizationReader) for(Map<String,Object> c:mapper.companies()) if(canReadCompany(id(c,"companyDeptId"),actor)) companies.add(c);
        return map("companies",companies,"currentUserId",actor);
    }

    @Transactional
    public Map<String, Object> addMapping(Long connectionId, Map<String, Object> input, Long actor)
    {
        Map<String, Object> c = lock(connectionId); requireIdle(c);
        mapper.lockTenant(string(c,"tenantKey"));
        Long userId = requiredId(input, "userId");
        Map<String, Object> staff = mapper.staff(userId);
        if (staff == null || !"0".equals(string(staff, "delFlag")) || !Objects.equals(id(c,"companyDeptId"), id(staff,"companyDeptId")))
            fail("人员必须明确属于连接公司，不能按姓名合并外部身份");
        LocalDate from = date(input, "effectiveFrom"); LocalDate to = optionalDate(input.get("effectiveTo"));
        if (to != null && to.isBefore(from)) fail("映射生效区间不正确");
        String external = requiredText(input, "externalUserId", 128);
        if (!external.matches("[A-Za-z0-9_-]+")) fail("飞书 employee_id 格式不正确");
        Map<String, Object> row = map("connectionId", connectionId, "tenantKey",c.get("tenantKey"), "userId", userId, "externalUserId", external,
            "effectiveFrom", from.toString(), "effectiveTo", to == null ? null : to.toString(), "actorId", actor);
        if (mapper.mappingConflicts(row) > 0) fail("该人员或外部身份在此有效期存在映射，须先明确旧映射截止日期");
        mapper.insertMapping(row); mapper.bumpVersion(connectionId);
        audit(connectionId, "CREATE_MAPPING", actor, id(row,"mappingId"), "人工确认 employee_id 与内部 user_id；历史来源归属不改写");
        return mapper.mapping(id(row,"mappingId"));
    }

    @Transactional
    public void retireMapping(Long mappingId, Map<String, Object> input, Long actor)
    {
        Map<String, Object> existing = mapper.mapping(mappingId);
        if (existing == null) fail("映射不存在");
        Map<String, Object> c = lock(id(existing,"connectionId")); requireIdle(c);
        existing = mapper.mapping(mappingId);
        LocalDate to = date(input,"effectiveTo"), from = optionalDate(existing.get("effectiveFrom"));
        if (to.isBefore(from)) fail("映射截止日期不能早于开始日期");
        String last = mapper.lastMappedDate(mappingId);
        if (last != null && to.isBefore(LocalDate.parse(last))) fail("该日期之后已有来源记录；不可更改历史身份归属");
        if (!"CONFIRMED".equals(string(existing,"status"))) fail("映射已停用");
        String reason = requiredText(input,"reason",1000);
        if(mapper.retireMapping(map("mappingId",mappingId,"effectiveTo",to.toString(),"actorId",actor))!=1)fail("映射状态已变化，请刷新后重试");
        mapper.bumpVersion(id(c,"connectionId")); audit(id(c,"connectionId"),"RETIRE_MAPPING",actor,mappingId,reason);
    }

    public List<Map<String, Object>> runs(Long connectionId) { connection(connectionId); return mapper.runs(connectionId); }
    public List<Map<String, Object>> issues(Long connectionId) { connection(connectionId); return mapper.issues(connectionId); }

    @Transactional
    public void resolveIssue(Long connectionId, Long issueId, Map<String,Object> input, Long actor)
    {
        lock(connectionId);
        Map<String,Object> issue = mapper.issue(issueId);
        if (issue == null || !Objects.equals(connectionId,id(issue,"connectionId"))) fail("问题不属于此连接");
        String resolution = requiredText(input,"resolution",1000);
        if (mapper.resolveIssue(map("issueId",issueId,"resolution",resolution,"actorId",actor)) != 1) fail("问题已处理");
        audit(connectionId,"RESOLVE_ISSUE",actor,issueId,resolution);
    }

    /** Manual replay is fenced by a database lease, including across multiple application nodes. */
    public Map<String, Object> sync(Long connectionId, Map<String, Object> input, Long actor)
    {
        Map<String,Object> prepared=prepareSync(connectionId,input,actor);
        return executeSync(connectionId,(Map<String,Object>)prepared.get("connection"),(Map<String,Object>)prepared.get("run"),
            (LocalDate)prepared.get("from"),(LocalDate)prepared.get("to"));
    }

    /** Return the persisted run immediately; the existing bounded application executor performs I/O. */
    public Map<String,Object> startSync(Long connectionId,Map<String,Object> input,Long actor)
    {
        Map<String,Object> prepared=prepareSync(connectionId,input,actor);
        Map<String,Object> run=(Map<String,Object>)prepared.get("run");
        try
        {
            executor.execute(() -> {
                try { executeSync(connectionId,(Map<String,Object>)prepared.get("connection"),run,
                    (LocalDate)prepared.get("from"),(LocalDate)prepared.get("to")); }
                catch(Exception failure) { abortRun(connectionId,id(run,"runId"),safeError(failure)); }
            });
        }
        catch(Exception rejected) { abortRun(connectionId,id(run,"runId"),"FEISHU_EXECUTOR_UNAVAILABLE"); fail("同步队列暂不可用，请稍后重试"); }
        return map("connectionId",connectionId,"runId",run.get("runId"),"status","RUNNING",
            "windowStart",run.get("windowStart"),"windowEnd",run.get("windowEnd"));
    }

    private Map<String,Object> prepareSync(Long connectionId,Map<String,Object> input,Long actor)
    {
        LocalDate from = date(input,"windowStart"), to = date(input,"windowEnd");
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from,to) > 6) fail("每次补拉最多 7 个自然日，请分段执行");
        final Map<String,Object> c = connection(connectionId);
        if (to.isAfter(LocalDate.now(ZoneId.of(string(c,"timezone"))).plusDays(1))) fail("飞书审批查询最多支持到明天");
        if (!provider.isConfigured(string(c,"tenantKey"))) fail("服务端飞书凭据未配置或企业标识不匹配，尚未发起同步");
        Map<String,Object> run = transaction.execute(status -> {
            Map<String,Object> current = lock(connectionId);
            if (current.get("runningRunId") != null && !expired(current.get("leaseUntil"))) fail("该连接正在同步，请等待当前批次完成");
            List<Map<String,Object>> maps = mapper.mappings(connectionId);
            if (maps.isEmpty()) fail("请先建立明确的人员映射");
            Map<String,Object> r = map("connectionId",connectionId,"mappingVersion",current.get("version"),
                "windowStart",from.toString(),"windowEnd",to.toString(),"actorId",actor);
            mapper.insertRun(r);
            if (mapper.acquireRun(r) != 1) fail("连接同步被其他进程占用");
            if (current.get("runningRunId") != null)
                mapper.finishRun(map("runId",current.get("runningRunId"),"status","FAILED","receivedCount",0,"rejectedCount",0,
                    "completedChunks",0,"expectedChunks",0,"errorCode","LEASE_EXPIRED"));
            audit(connectionId,"START_SYNC",actor,id(r,"runId"),from + " 至 " + to);
            return r;
        });
        return map("connection",c,"run",run,"from",from,"to",to);
    }

    private Map<String,Object> executeSync(Long connectionId,Map<String,Object> c,Map<String,Object> run,LocalDate from,LocalDate to)
    {
        Long runId = id(run,"runId");
        int received=0, rejected=0, completed=0, expected=0;
        String error=null;
        List<Map<String,Object>> maps = mapper.mappings(connectionId);
        for (LocalDate day=from; !day.isAfter(to); day=day.plusDays(1)) expected += ((activeMappings(maps,day).size()+9)/10)*3;
        final int expectedCount=expected;
        transaction.execute(status -> {requireRun(connectionId,runId);mapper.expectedChunks(map("runId",runId,"expectedChunks",expectedCount));return null;});
        if (expected == 0) error="NO_EFFECTIVE_MAPPINGS";
        outer: for (LocalDate day=from; !day.isAfter(to) && error==null; day=day.plusDays(1))
        {
            List<Map<String,Object>> active=activeMappings(maps,day);
            if (active.isEmpty()) { error="NO_EFFECTIVE_MAPPINGS"; break; }
            for (int offset=0; offset<active.size(); offset+=10)
            {
                List<Map<String,Object>> part=active.subList(offset,Math.min(offset+10,active.size()));
                List<String> ids=new ArrayList<>(); for(Map<String,Object> m:part) ids.add(string(m,"externalUserId"));
                for (String resource:Arrays.asList("APPROVAL","TASK","SHIFT"))
                {
                    Map<String,Object> chunk=map("runId",runId,"connectionId",connectionId,"resource",resource,
                        "businessDate",day.toString(),"chunkNo",offset/10,"scopeHash",sha256(encode(ids)),"receivedCount",0,"errorCode",null);
                    try
                    {
                        List<Map<String,Object>> rows=provider.query(string(c,"tenantKey"),string(c,"timezone"),resource,ids,day);
                        // No sortable source version: reread the same complete chunk, serialised under the lease.
                        // A changing response cannot overwrite a newer snapshot by arrival order alone.
                        List<Map<String,Object>> verified=provider.query(string(c,"tenantKey"),string(c,"timezone"),resource,ids,day);
                        if (!canonical(rows).equals(canonical(verified))) fail("FEISHU_SOURCE_CHANGED_DURING_READ");
                        chunk.put("receivedCount",rows.size()); chunk.put("status","COMPLETE");
                        transaction.execute(status -> {
                            requireRun(connectionId,runId);
                            for(Map<String,Object> row:rows) storeObservation(connectionId,runId,row,part);
                            mapper.insertChunk(chunk); mapper.advanceProgress(chunk); return null;
                        });
                        received+=rows.size(); completed++;
                    }
                    catch (Exception ex)
                    {
                        error=safeError(ex); rejected++;
                        chunk.put("status","FAILED"); chunk.put("errorCode",error);
                        final String code=error;
                        transaction.execute(status -> {
                            requireRun(connectionId,runId); mapper.insertChunk(chunk);
                            mapper.insertIssue(map("connectionId",connectionId,"runId",runId,"observationId",null,
                                "externalIdHash",null,"issueCode",code)); return null;
                        });
                        break outer;
                    }
                }
            }
        }
        Map<String,Object> finished=map("connectionId",connectionId,"runId",runId,"status",error==null && completed==expected ? "COMPLETE" : completed>0 ? "PARTIAL" : "FAILED",
            "receivedCount",received,"rejectedCount",rejected,"completedChunks",completed,"expectedChunks",expected,"errorCode",error);
        transaction.execute(status -> { requireRun(connectionId,runId); mapper.finishRun(finished); mapper.releaseRun(finished); return null; });
        return mapper.run(runId);
    }

    private void abortRun(Long connectionId,Long runId,String code)
    {
        transaction.execute(status -> {
            Map<String,Object> c=lock(connectionId);
            // A former lease holder cannot clear a newer run or change its watermark.
            if(!Objects.equals(runId,id(c,"runningRunId"))) return null;
            Map<String,Object> old=mapper.run(runId);
            Map<String,Object> failed=map("connectionId",connectionId,"runId",runId,"status","FAILED",
                "receivedCount",old==null?0:number(old.get("receivedCount")),"rejectedCount",1,
                "completedChunks",old==null?0:number(old.get("completedChunks")),"expectedChunks",old==null?0:number(old.get("expectedChunks")),"errorCode",code);
            mapper.finishRun(failed);mapper.releaseRun(failed);return null;
        });
    }

    private void storeObservation(Long connectionId,Long runId,Map<String,Object> source,List<Map<String,Object>> mappings)
    {
        Map<String,Object> mapping=null;
        for(Map<String,Object> m:mappings) if(Objects.equals(m.get("externalUserId"),source.get("externalUserId")))
        { if(mapping!=null) fail("FEISHU_MAPPING_CONFLICT"); mapping=m; }
        if(mapping==null) fail("FEISHU_UNMAPPED_ID");
        Map<String,Object> row=new LinkedHashMap<>(source);
        row.put("fingerprint",sha256(encode(source)));
        row.put("sourceRecordKey",sha256(string(source,"sourceRecordKey")));
        row.put("connectionId",connectionId); row.put("mappingId",mapping.get("mappingId")); row.put("userId",mapping.get("userId"));
        row.put("runId",runId); row.put("intervalsJson",encode(source.get("intervals"))); row.put("detailsJson",encode(source.get("sourceDetails")));
        row.putIfAbsent("sourceDurationSeconds",null);
        Map<String,Object> old=mapper.currentObservation(row);
        if(old!=null && !Objects.equals(id(old,"userId"),id(row,"userId"))) fail("FEISHU_HISTORICAL_IDENTITY_CONFLICT");
        if(old!=null && Objects.equals(old.get("fingerprint"),row.get("fingerprint")))
        { mapper.touchObservation(map("observationId",old.get("observationId"),"runId",runId)); return; }
        row.put("sourceRevision",old==null ? 1 : number(old.get("sourceRevision"))+1);
        if(old!=null) mapper.supersedeObservation(id(old,"observationId"));
        mapper.insertObservation(row);
        if(old!=null || "UNKNOWN".equals(string(row,"quality")))
            mapper.insertIssue(map("connectionId",connectionId,"runId",runId,"observationId",row.get("observationId"),"externalIdHash",null,
                "issueCode",old!=null ? "SOURCE_REVISION_REQUIRES_REVIEW" : "UNKNOWN_SOURCE_STATUS"));
    }

    /** Employee self-service and explicitly scoped HR read permission, independent of integration administration. */
    public List<Map<String,Object>> records(Map<String,Object> input, Long actor, boolean organizationReader)
    {
        Map<String,Object> q=new LinkedHashMap<>();
        LocalDate from=date(input,"dateFrom"),to=date(input,"dateTo");
        if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>92) fail("只读查询每次最多 93 天");
        Long companyId=id(input,"companyDeptId"),userId=id(input,"userId");
        if(userId==null && companyId==null) userId=actor;
        if(!Objects.equals(userId,actor))
        {
            if(!organizationReader || companyId==null || !canReadCompany(companyId,actor)) fail("没有该公司假勤数据权限");
        }
        q.put("companyDeptId",companyId); q.put("userId",userId); q.put("dateFrom",from.toString()); q.put("dateTo",to.toString());
        q.put("includeHistory",Boolean.TRUE.equals(input.get("includeHistory")) || "true".equals(String.valueOf(input.get("includeHistory"))));
        int page=input.get("pageNum")==null?1:number(input.get("pageNum")),size=input.get("pageSize")==null?50:number(input.get("pageSize"));
        if(page<1||page>100000||size<1||size>1000)fail("分页范围不正确");q.put("offset",(page-1)*size);q.put("pageSize",size);
        List<Map<String,Object>> rows=mapper.records(q);
        Map<Long,String> connectionQuality=new HashMap<>();Map<Long,List<Map<String,Object>>> connectionRuns=new HashMap<>();
        for(Map<String,Object> row:rows)
        {
            row.remove("fingerprint"); row.remove("sourceRecordKey");
            row.put("sourceQuality",row.get("quality"));
            Long connectionId=id(row,"connectionId");
            if(connectionId!=null&&!connectionQuality.containsKey(connectionId))
            {
                List<Map<String,Object>> history=mapper.runs(connectionId);
                connectionRuns.put(connectionId,history);
                connectionQuality.put(connectionId,!history.isEmpty()&&Arrays.asList("FAILED","PARTIAL").contains(string(history.get(0),"status"))?"PARTIAL":"KNOWN");
            }
            if(!recent(row.get("lastSeenAt"))) row.put("quality","STALE");
            else if("PARTIAL".equals(connectionQuality.get(connectionId)))row.put("quality","PARTIAL");
            LocalDate businessDate=optionalDate(row.get("businessDate"));Long lastSeenRun=id(row,"lastSeenRunId");
            if(number(row.get("isCurrent"))==1&&businessDate!=null&&lastSeenRun!=null)
            {
                for(Map<String,Object> run:connectionRuns.getOrDefault(connectionId,Collections.emptyList()))
                {
                    LocalDate windowStart=optionalDate(run.get("windowStart")),windowEnd=optionalDate(run.get("windowEnd"));
                    if("COMPLETE".equals(string(run,"status"))&&windowStart!=null&&windowEnd!=null&&!businessDate.isBefore(windowStart)&&!businessDate.isAfter(windowEnd))
                    {
                        if(id(run,"runId")!=null&&id(run,"runId")>lastSeenRun)
                        {
                            if(!"STALE".equals(row.get("quality")))row.put("quality","PARTIAL");
                            row.put("qualityReason","NOT_SEEN_IN_LATEST_COMPLETE");
                        }
                        break;
                    }
                }
            }
            row.put("source","FEISHU");
        }
        return rows;
    }

    public Map<String,Object> cutoverStatus(Long connectionId, String effectiveDate, Long actor)
    {
        Map<String,Object> c=connection(connectionId); requireCompanyLeader(id(c,"companyDeptId"),actor);
        LocalDate day=effectiveDate==null ? LocalDate.now(ZoneId.of(string(c,"timezone"))).plusDays(1) : LocalDate.parse(effectiveDate);
        Map<String,Object> query=map("connectionId",connectionId,"companyDeptId",c.get("companyDeptId"),"effectiveDate",day.toString(),"version",c.get("version"));
        List<String> blockers=new ArrayList<>();
        if(!provider.isConfigured(string(c,"tenantKey"))) blockers.add("服务端凭据未配置或企业标识不匹配");
        if(!"PARALLEL".equals(string(c,"state"))) blockers.add("来源已切换，不能重复启用或自动恢复本地申请");
        if(c.get("runningRunId")!=null) blockers.add("同步仍在运行");
        if(day.isBefore(LocalDate.now(ZoneId.of(string(c,"timezone"))).plusDays(1))) blockers.add("生效日至少为来源时区的明天");
        if(!recent(c.get("lastCompleteAt"))) blockers.add("缺少最近 48 小时完整同步");
        Map<String,Object> last=id(c,"lastCompleteRunId")==null ? null : mapper.run(id(c,"lastCompleteRunId"));
        if(last==null || number(last.get("mappingVersion"))!=number(c.get("version"))) blockers.add("当前人员映射版本尚无完整同步");
        if(last!=null && optionalDate(last.get("windowEnd")).isBefore(LocalDate.now(ZoneId.of(string(c,"timezone"))).minusDays(1))) blockers.add("完整同步窗口尚未覆盖最近业务日期");
        int missing=mapper.unmappedStaff(query),pending=mapper.pendingLocalLeave(id(c,"companyDeptId")),crossing=mapper.crossingLocalLeave(query),issues=mapper.openIssues(connectionId),cases=mapper.validatedCases(query);
        if(missing>0) blockers.add("生效日仍有 "+missing+" 名在职人员缺少映射");
        if(pending>0) blockers.add("仍有 "+pending+" 条本地待审批/待撤销申请");
        if(crossing>0) blockers.add("仍有 "+crossing+" 条已批准本地请假跨越生效日，需先明确收尾");
        if(issues>0) blockers.add("仍有 "+issues+" 条同步或来源差异待处理");
        if(cases<CASES.size()) blockers.add("真实企业验收仅通过 "+cases+"/"+CASES.size()+" 类情景");
        return map("connectionId",connectionId,"state",c.get("state"),"version",c.get("version"),"effectiveDate",day.toString(),
            "canActivate",blockers.isEmpty(),"blockers",blockers,"unmappedStaffCount",missing,"pendingLocalCount",pending,
            "crossingLocalCount",crossing,"openIssueCount",issues,"validatedCaseCount",cases,"requiredCases",CASES,"validations",mapper.validations(connectionId));
    }

    @Transactional
    public void validate(Long connectionId,Map<String,Object> input,Long actor)
    {
        Map<String,Object> c=lock(connectionId); requireCompanyLeader(id(c,"companyDeptId"),actor); requireIdle(c);
        String type=requiredText(input,"caseType",24); if(!CASES.contains(type)) fail("验收情景无效");
        Long runId=requiredId(input,"runId"); Map<String,Object> run=mapper.run(runId);
        if(run==null || !Objects.equals(connectionId,id(run,"connectionId")) || !"COMPLETE".equals(string(run,"status"))) fail("须引用此连接实际完成的同步批次");
        if(number(run.get("mappingVersion"))!=number(c.get("version"))) fail("人员映射已变更，请重新同步和核验");
        Long observationId=id(input,"observationId"),issueId=id(input,"issueId");
        Map<String,Object> observation=observationId==null?null:mapper.observation(observationId);
        if(!"UNMAPPED".equals(type)&&!"RECOVERY".equals(type))
        {
            if(observation==null||!Objects.equals(connectionId,id(observation,"connectionId"))) fail("须引用本连接的真实来源记录");
            if(!Objects.equals(runId,id(observation,"syncRunId"))&&!Objects.equals(runId,id(observation,"lastSeenRunId"))) fail("来源记录须在所选真实同步批次中实际读取过");
            if("NORMAL".equals(type)&&(!"ATTENDANCE".equals(string(observation,"kind"))||!normalAttendance(observation))) fail("正常样本须引用有正常打卡结果的真实考勤记录");
            if("LEAVE".equals(type)&&(!"LEAVE".equals(string(observation,"kind"))||!"CONFIRMED".equals(string(observation,"normalizedStatus")))) fail("请假样本须引用已通过假勤结果");
            if("CORRECTION".equals(type)&&number(observation.get("sourceRevision"))<2) fail("更正样本须具有可追溯来源修订");
            if("CROSS_DAY".equals(type)&&!crossesDay(observation)) fail("跨日样本须有实际跨日时间区间");
        }
        else
        {
            Map<String,Object> issue=issueId==null?null:mapper.issue(issueId);
            if(issue==null||!Objects.equals(connectionId,id(issue,"connectionId"))||!"RESOLVED".equals(string(issue,"status"))) fail("须引用已核实并处理的真实同步问题");
            String code=string(issue,"issueCode");
            if("UNMAPPED".equals(type)&&!(code.contains("MAPPED")||code.contains("SCOPE")||code.contains("IDENTITY"))) fail("未匹配情景须引用实际身份/授权范围问题");
            if("RECOVERY".equals(type)&&!(code.contains("HTTP")||code.contains("NETWORK")||code.contains("RETRY")||code.contains("LEASE")||code.contains("SCOPE"))) fail("恢复情景须引用真实通信、授权范围或执行中断问题");
            if(id(issue,"runId")!=null && runId<=id(issue,"runId")) fail("恢复验收须引用问题发生后完成的补拉批次");
        }
        Map<String,Object> row=map("connectionId",connectionId,"connectionVersion",c.get("version"),"caseType",type,
            "observationId",observationId,"issueId",issueId,"runId",runId,"expectedResult",requiredText(input,"expectedResult",1000),
            "sourceEvidence",requiredText(input,"sourceEvidence",1000),"localResult",requiredText(input,"localResult",1000),
            "passed",Boolean.TRUE.equals(input.get("passed"))?1:0,"reason",requiredText(input,"reason",1000),"actorId",actor);
        mapper.insertValidation(row); audit(connectionId,"RECORD_VALIDATION",actor,observationId,type+"："+row.get("reason"));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> activate(Long connectionId,Map<String,Object> input,Long actor)
    {
        Map<String,Object> before=connection(connectionId);
        mapper.lockCompany(id(before,"companyDeptId")); // same company row acquired by local leave guards
        Map<String,Object> c=lock(connectionId); requireCompanyLeader(id(c,"companyDeptId"),actor);
        LocalDate effective=date(input,"effectiveDate"); String reason=requiredText(input,"reason",1000);
        if(number(input.get("version"))!=number(c.get("version"))) fail("连接已变更，请刷新后重试");
        Map<String,Object> checks=cutoverStatus(connectionId,effective.toString(),actor);
        if(!Boolean.TRUE.equals(checks.get("canActivate"))) fail("切换条件未满足："+checks.get("blockers"));
        if(mapper.activate(map("connectionId",connectionId,"effectiveDate",effective.toString(),"version",c.get("version")))!=1) fail("连接状态已变化");
        audit(connectionId,"ACTIVATE_AUTHORITY",actor,null,reason+"；生效日="+effective);
        return mapper.connection(connectionId);
    }

    /** Invoke inside the existing leave transaction before new request/approval mutations. */
    @Transactional
    public void requireLocalLeaveAllowed(Long companyDeptId, java.util.Date startDate, java.util.Date endDate)
    {
        if(companyDeptId==null) return;
        mapper.lockCompany(companyDeptId);
        Map<String,Object> c=mapper.connectionForCompany(companyDeptId);
        if(c==null||!"ACTIVE".equals(string(c,"state"))) return;
        LocalDate effective=optionalDate(c.get("effectiveDate"));
        LocalDate start=optionalDate(startDate),end=optionalDate(endDate);
        if(effective==null||start==null||end==null||!end.isBefore(effective)) fail("该公司此日期起由飞书办理假勤，本地仅保留历史查询；同步故障不会恢复本地申请");
    }

    public Map<String,Object> getAuthority(Long companyDeptId,java.util.Date date)
    {
        Map<String,Object> c=companyDeptId==null?null:mapper.connectionForCompany(companyDeptId);
        LocalDate day=optionalDate(date),effective=c==null?null:optionalDate(c.get("effectiveDate"));
        boolean external=c!=null&&"ACTIVE".equals(string(c,"state"))&&effective!=null&&day!=null&&!day.isBefore(effective);
        return map("source",external?"FEISHU":"LOCAL_LEGACY","localLeaveAllowed",!external,
            "effectiveDate",effective==null?null:effective.toString(),"quality",c!=null&&recent(c.get("lastCompleteAt"))?"KNOWN":"UNKNOWN");
    }

    @Transactional
    public void requireLocalLeaveForPerson(Long userId,Long projectCompanyDeptId,java.util.Date from,java.util.Date to)
    {
        SortedSet<Long> companies=personCompanies(userId,projectCompanyDeptId);
        for(Long company:companies)mapper.lockCompany(company);
        for(Long company:companies)requireLocalLeaveAllowed(company,from,to);
    }

    public Map<String,Object> getPersonAuthority(Long userId,Long projectCompanyDeptId,java.util.Date date)
    {
        SortedSet<Long> companies=personCompanies(userId,projectCompanyDeptId);
        Map<String,Object> result=map("source","LOCAL_LEGACY","localLeaveAllowed",true,"effectiveDate",null,"quality","UNKNOWN");
        for(Long company:companies)
        {
            Map<String,Object> authority=getAuthority(company,date);
            if("FEISHU".equals(authority.get("source")))result=new LinkedHashMap<>(authority);
        }
        result.put("companyDeptIds",new ArrayList<>(companies));return result;
    }

    private SortedSet<Long> personCompanies(Long userId,Long projectCompanyDeptId)
    {
        SortedSet<Long> companies=new TreeSet<>();if(projectCompanyDeptId!=null)companies.add(projectCompanyDeptId);
        Map<String,Object> person=userId==null?null:mapper.staff(userId);
        if(person!=null&&id(person,"companyDeptId")!=null)companies.add(id(person,"companyDeptId"));
        return companies;
    }

    /** Availability is a derived read view; this does not write actual effort or costs. */
    public Map<String,Object> availability(Long companyDeptId,Long userId,LocalDate day,Long actor,boolean organizationReader)
    {
        List<Map<String,Object>> rows=records(map("companyDeptId",companyDeptId,"userId",userId,"dateFrom",day.toString(),"dateTo",day.toString(),"pageSize",1000),actor,organizationReader);
        List<long[]> work=null,leave=new ArrayList<>(); boolean stale=false,partial=rows.size()>=1000;
        for(Map<String,Object> row:rows)
        {
            if("STALE".equals(row.get("quality"))) stale=true;
            if("PARTIAL".equals(row.get("quality"))||"UNKNOWN".equals(row.get("quality"))) partial=true;
            if("SHIFT".equals(row.get("kind"))&&"CONFIRMED".equals(row.get("normalizedStatus"))) work=intervals(row);
            if("LEAVE".equals(row.get("kind"))&&"CONFIRMED".equals(row.get("normalizedStatus"))) leave.addAll(intervals(row));
        }
        Map<String,Object> authority=getAuthority(companyDeptId,java.sql.Date.valueOf(day));
        Map<String,Object> c=mapper.connectionForCompany(companyDeptId);
        Map<String,Object> last=c==null||id(c,"lastCompleteRunId")==null?null:mapper.run(id(c,"lastCompleteRunId"));
        boolean covered=last!=null&&"COMPLETE".equals(string(last,"status"))&&recent(c.get("lastCompleteAt"))
            &&number(last.get("mappingVersion"))==number(c.get("version"))
            &&!day.isBefore(optionalDate(last.get("windowStart")))&&!day.isAfter(optionalDate(last.get("windowEnd")));
        if(work==null||stale||partial||!covered||!"FEISHU".equals(authority.get("source"))) return map("userId",userId,"businessDate",day.toString(),"quality",stale?"STALE":partial?"PARTIAL":"UNKNOWN",
            "plannedCapacityMinutes",null,"approvedUnavailableMinutes",null,"availableMinutes",null,"authority",authority,"source","FEISHU_READONLY");
        long capacity=AttendanceIntervals.minutes(work),available=AttendanceIntervals.minutes(AttendanceIntervals.subtract(work,leave));
        return map("userId",userId,"businessDate",day.toString(),"quality","KNOWN","plannedCapacityMinutes",capacity,
            "approvedUnavailableMinutes",capacity-available,"availableMinutes",available,"authority",authority,"source","FEISHU_READONLY");
    }

    @Transactional
    public void authorizeReader(Long connectionId,Map<String,Object> input,Long actor)
    {
        Map<String,Object> c=lock(connectionId); Long companyId=id(c,"companyDeptId"); requireCompanyLeader(companyId,actor);
        Long userId=requiredId(input,"userId"); if(mapper.staff(userId)==null) fail("授权人员不存在");
        String reason=requiredText(input,"reason",1000);
        mapper.saveReader(map("companyDeptId",companyId,"userId",userId,"enabled",Boolean.TRUE.equals(input.get("enabled"))?1:0,"actorId",actor));
        audit(connectionId,"AUTHORIZE_READER",actor,userId,reason);
    }
    public List<Map<String,Object>> readers(Long connectionId,Long actor)
    { Map<String,Object> c=connection(connectionId); requireCompanyLeader(id(c,"companyDeptId"),actor); return mapper.readers(id(c,"companyDeptId")); }
    private boolean canReadCompany(Long company,Long user)
    { Map<String,Object> c=mapper.company(company); return c!=null&&(Objects.equals(user,id(c,"leaderUserId"))||mapper.readerAllowed(company,user)>0); }
    private void requireCompanyLeader(Long company,Long user)
    { Map<String,Object> c=mapper.company(company); if(c==null||!Objects.equals(user,id(c,"leaderUserId"))) fail("此操作须由该公司实际负责人执行，技术管理权限不授予业务验收或来源切换权"); }
    private Map<String,Object> connection(Long id) { Map<String,Object> c=mapper.connection(id); if(c==null) fail("飞书连接不存在"); return c; }
    private Map<String,Object> lock(Long id) { Map<String,Object> c=mapper.lockConnection(id); if(c==null) fail("飞书连接不存在"); return c; }
    private void requireIdle(Map<String,Object> c) { if(c.get("runningRunId")!=null) fail("同步期间不能更改映射或切换来源"); }
    private void requireRun(Long connection,Long run) { if(!Objects.equals(run,id(lock(connection),"runningRunId"))) fail("FEISHU_LEASE_LOST"); }
    private void audit(Long connection,String action,Long actor,Long reference,String reason)
    { mapper.insertAudit(map("connectionId",connection,"action",action,"actorId",actor,"referenceId",reference,"reason",reason)); }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception ex){throw new ServiceException("FEISHU_SERIALIZATION_ERROR");} }
    private String canonical(List<Map<String,Object>> rows) { List<String> values=new ArrayList<>(); for(Map<String,Object> row:rows) values.add(encode(row)); Collections.sort(values); return encode(values); }
    private List<long[]> intervals(Map<String,Object> row) { try{return json.readValue(string(row,"intervalsJson"),new TypeReference<List<long[]>>(){});}catch(Exception ex){throw new ServiceException("来源区间无法解析，待核实");} }
    private boolean crossesDay(Map<String,Object> row) { ZoneId zone=ZoneId.of(string(row,"sourceTimezone")); for(long[] p:intervals(row)) if(!Instant.ofEpochSecond(p[0]).atZone(zone).toLocalDate().equals(Instant.ofEpochSecond(p[1]-1).atZone(zone).toLocalDate())) return true; return false; }
    private boolean normalAttendance(Map<String,Object> row)
    {
        try
        {
            com.fasterxml.jackson.databind.JsonNode results=json.readTree(string(row,"detailsJson")).path("results");
            boolean normal=false;
            if(!results.isArray()||results.size()==0)return false;
            for(com.fasterxml.jackson.databind.JsonNode p:results)for(String key:Arrays.asList("checkInResult","checkOutResult"))
            { String state=p.path(key).asText();if("Normal".equals(state))normal=true;else if(!"NoNeedCheck".equals(state))return false; }
            return normal;
        }
        catch(Exception ex){return false;}
    }
    private static List<Map<String,Object>> activeMappings(List<Map<String,Object>> all,LocalDate date)
    { List<Map<String,Object>> result=new ArrayList<>(); for(Map<String,Object> m:all) { LocalDate from=optionalDate(m.get("effectiveFrom")),to=optionalDate(m.get("effectiveTo")); if(from!=null&&!date.isBefore(from)&&(to==null||!date.isAfter(to))) result.add(m); } return result; }
    private static String safeError(Exception ex) { String message=ex.getMessage(); return message!=null&&message.matches("FEISHU_[A-Z0-9_]{1,80}")?message:"FEISHU_SYNC_FAILED"; }
    private static boolean recent(Object value) { return value instanceof java.util.Date && ((java.util.Date)value).getTime()>=System.currentTimeMillis()-48L*3600*1000; }
    private static boolean expired(Object value) { return !(value instanceof java.util.Date)||((java.util.Date)value).getTime()<System.currentTimeMillis(); }
    private static LocalDate date(Map<String,Object> value,String key) { LocalDate date=optionalDate(value.get(key)); if(date==null) fail("缺少日期："+key); return date; }
    private static LocalDate optionalDate(Object value) { if(value==null||String.valueOf(value).isEmpty())return null; if(value instanceof java.sql.Date)return ((java.sql.Date)value).toLocalDate(); if(value instanceof java.util.Date)return Instant.ofEpochMilli(((java.util.Date)value).getTime()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate(); try{return LocalDate.parse(String.valueOf(value).substring(0,10));}catch(Exception ex){throw new ServiceException("日期格式须为 yyyy-MM-dd");} }
    private static Long requiredId(Map<String,Object> row,String key) { Long value=id(row,key); if(value==null||value<=0)fail("缺少有效标识："+key); return value; }
    private static Long id(Map<String,Object> row,String key) { Object value=row.get(key); if(value==null||String.valueOf(value).isEmpty())return null; try{return Long.valueOf(String.valueOf(value));}catch(Exception ex){throw new ServiceException("标识格式不正确："+key);} }
    private static int number(Object value) { return value==null?0:Integer.parseInt(String.valueOf(value)); }
    private static String string(Map<String,Object> row,String key) { return row.get(key)==null?"":String.valueOf(row.get(key)); }
    private static String requiredText(Map<String,Object> row,String key,int max) { String value=string(row,key).trim(); if(value.isEmpty()||value.length()>max)fail(key+" 必填且不能超过 "+max+" 字"); return value; }
    private static void fail(String message) { throw new ServiceException(message); }
}
