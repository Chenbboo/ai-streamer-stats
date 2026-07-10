package com.ruoyi.live.service;

import java.util.List;
import com.ruoyi.live.domain.LiveKpiConfig;

/**
 * KPI 配置 服务层
 */
public interface ILiveKpiConfigService
{
    /**
     * 查询 KPI 配置列表
     */
    public List<LiveKpiConfig> selectKpiConfigList(LiveKpiConfig config);

    /**
     * 查询指定主播指定月份的 KPI 配置
     */
    public LiveKpiConfig selectKpiConfig(Long streamerId, Integer year, Integer month);

    /**
     * 新增 KPI 配置
     */
    public int insertKpiConfig(LiveKpiConfig config);

    /**
     * 修改 KPI 配置
     */
    public int updateKpiConfig(LiveKpiConfig config);

    /**
     * 删除 KPI 配置
     */
    public int deleteKpiConfigByIds(Long[] kpiIds);
}
