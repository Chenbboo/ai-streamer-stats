package com.ruoyi.web.controller.live;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.live.domain.LiveKpiConfig;
import com.ruoyi.live.service.ILiveKpiConfigService;

/**
 * KPI 配置 Controller
 */
@RestController
@RequestMapping("/live/kpi")
public class LiveKpiConfigController extends BaseController
{
    @Autowired
    private ILiveKpiConfigService kpiConfigService;

    /**
     * 查询 KPI 配置列表
     */
    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/list")
    public TableDataInfo list(LiveKpiConfig config)
    {
        startPage();
        List<LiveKpiConfig> list = kpiConfigService.selectKpiConfigList(config);
        return getDataTable(list);
    }

    /**
     * 获取 KPI 配置详情
     */
    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/{kpiId}")
    public AjaxResult getInfo(@PathVariable Long kpiId)
    {
        LiveKpiConfig config = new LiveKpiConfig();
        config.setKpiId(kpiId);
        List<LiveKpiConfig> list = kpiConfigService.selectKpiConfigList(config);
        return list.isEmpty() ? error("配置不存在") : success(list.get(0));
    }

    /**
     * 获取指定主播指定月份的 KPI 配置
     */
    @PreAuthorize("@ss.hasPermi('live:stats:list')")
    @GetMapping("/get")
    public AjaxResult getKpiConfig(Long streamerId, Integer year, Integer month)
    {
        LiveKpiConfig config = kpiConfigService.selectKpiConfig(streamerId, year, month);
        return success(config);
    }

    /**
     * 新增 KPI 配置
     */
    @PreAuthorize("@ss.hasPermi('live:stats:edit')")
    @Log(title = "KPI 配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody LiveKpiConfig config)
    {
        return toAjax(kpiConfigService.insertKpiConfig(config));
    }

    /**
     * 修改 KPI 配置
     */
    @PreAuthorize("@ss.hasPermi('live:stats:edit')")
    @Log(title = "KPI 配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody LiveKpiConfig config)
    {
        return toAjax(kpiConfigService.updateKpiConfig(config));
    }

    /**
     * 删除 KPI 配置
     */
    @PreAuthorize("@ss.hasPermi('live:stats:remove')")
    @Log(title = "KPI 配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{kpiIds}")
    public AjaxResult remove(@PathVariable Long[] kpiIds)
    {
        return toAjax(kpiConfigService.deleteKpiConfigByIds(kpiIds));
    }
}
