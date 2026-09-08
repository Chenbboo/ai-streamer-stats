package com.ruoyi.web.controller.business;

import java.time.LocalDate;
import java.util.Map;
import com.ruoyi.business.attendance.BusinessFeishuService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/feishu")
public class BusinessFeishuController extends BaseController
{
    private final BusinessFeishuService service;
    public BusinessFeishuController(BusinessFeishuService service) { this.service=service; }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @GetMapping("/{connectionId}/directory") public AjaxResult directory(@PathVariable Long connectionId)
    { return success(service.directory(connectionId)); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @PostMapping("/{connectionId}/mappings/batch")
    @Log(title="飞书批量人员关联",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult batchMappings(@PathVariable Long connectionId,@RequestBody Map<String,Object> body)
    { return success(service.addMappings(connectionId,body,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @GetMapping("/{connectionId}/sync-overview") public AjaxResult overview(@PathVariable Long connectionId)
    { return success(service.syncOverview(connectionId)); }

    @PreAuthorize("@ss.hasAnyPermi('business:integration:feishu,business:attendance:cutover')")
    @GetMapping("/connections") public AjaxResult connections()
    { return success(service.connections(SecurityUtils.getUserId(),SecurityUtils.hasPermi("business:integration:feishu"))); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @Log(title="飞书只读连接",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/connections") public AjaxResult create(@RequestBody Map<String,Object> body)
    { return success(service.createConnection(body,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @GetMapping("/{connectionId}/people") public AjaxResult people(@PathVariable Long connectionId)
    { return success(service.people(connectionId)); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @GetMapping("/{connectionId}/mappings") public AjaxResult mappings(@PathVariable Long connectionId)
    { return success(service.mappings(connectionId)); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @Log(title="飞书人员映射",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{connectionId}/mappings") public AjaxResult mapping(@PathVariable Long connectionId,@RequestBody Map<String,Object> body)
    { return success(service.addMapping(connectionId,body,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @Log(title="飞书映射截止",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/mappings/{mappingId}/retire") public AjaxResult retire(@PathVariable Long mappingId,@RequestBody Map<String,Object> body)
    { service.retireMapping(mappingId,body,SecurityUtils.getUserId()); return success(); }

    @PreAuthorize("@ss.hasAnyPermi('business:integration:feishu,business:attendance:cutover')")
    @GetMapping("/{connectionId}/runs") public AjaxResult runs(@PathVariable Long connectionId)
    { if(!SecurityUtils.hasPermi("business:integration:feishu"))service.requireConnectionOwner(connectionId,SecurityUtils.getUserId());return success(service.runs(connectionId)); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @Log(title="飞书手动补拉",businessType=BusinessType.OTHER,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{connectionId}/sync") public AjaxResult sync(@PathVariable Long connectionId,@RequestBody Map<String,Object> body)
    { return success(service.startSync(connectionId,body,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasAnyPermi('business:integration:feishu,business:attendance:cutover')")
    @GetMapping("/{connectionId}/issues") public AjaxResult issues(@PathVariable Long connectionId)
    { if(!SecurityUtils.hasPermi("business:integration:feishu"))service.requireConnectionOwner(connectionId,SecurityUtils.getUserId());return success(service.issues(connectionId)); }

    @PreAuthorize("@ss.hasPermi('business:integration:feishu')")
    @Log(title="飞书同步问题处理",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{connectionId}/issues/{issueId}/resolve") public AjaxResult resolve(@PathVariable Long connectionId,@PathVariable Long issueId,@RequestBody Map<String,Object> body)
    { service.resolveIssue(connectionId,issueId,body,SecurityUtils.getUserId()); return success(); }

    @PreAuthorize("@ss.hasAnyPermi('business:attendance:self,business:attendance:read')")
    @GetMapping("/query-options") public AjaxResult queryOptions()
    { return success(service.queryOptions(SecurityUtils.getUserId(),SecurityUtils.hasPermi("business:attendance:read"))); }

    @PreAuthorize("@ss.hasAnyPermi('business:attendance:self,business:attendance:read')")
    @GetMapping("/records") public AjaxResult records(@RequestParam Map<String,Object> query)
    { return success(service.records(query,SecurityUtils.getUserId(),SecurityUtils.hasPermi("business:attendance:read"))); }

    @PreAuthorize("@ss.hasAnyPermi('business:attendance:self,business:attendance:read')")
    @GetMapping("/availability") public AjaxResult availability(@RequestParam Long companyDeptId,@RequestParam Long userId,@RequestParam String date)
    { return success(service.availability(companyDeptId,userId,LocalDate.parse(date),SecurityUtils.getUserId(),SecurityUtils.hasPermi("business:attendance:read"))); }

    @PreAuthorize("@ss.hasPermi('business:attendance:cutover')")
    @GetMapping("/{connectionId}/cutover-status") public AjaxResult cutover(@PathVariable Long connectionId,@RequestParam(required=false) String effectiveDate)
    { return success(service.cutoverStatus(connectionId,effectiveDate,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:attendance:cutover')")
    @Log(title="飞书真实样本验收",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{connectionId}/validation") public AjaxResult validate(@PathVariable Long connectionId,@RequestBody Map<String,Object> body)
    { service.validate(connectionId,body,SecurityUtils.getUserId()); return success(); }

    @PreAuthorize("@ss.hasPermi('business:attendance:cutover')")
    @Log(title="飞书假勤来源切换",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{connectionId}/activate") public AjaxResult activate(@PathVariable Long connectionId,@RequestBody Map<String,Object> body)
    { return success(service.activate(connectionId,body,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:attendance:cutover')")
    @GetMapping("/{connectionId}/readers") public AjaxResult readers(@PathVariable Long connectionId)
    { return success(service.readers(connectionId,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:attendance:cutover')")
    @GetMapping("/{connectionId}/reader-candidates") public AjaxResult readerCandidates(@PathVariable Long connectionId)
    { return success(service.readerCandidates(connectionId,SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('business:attendance:cutover')")
    @Log(title="公司假勤数据授权",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{connectionId}/readers") public AjaxResult reader(@PathVariable Long connectionId,@RequestBody Map<String,Object> body)
    { service.authorizeReader(connectionId,body,SecurityUtils.getUserId()); return success(); }
}
