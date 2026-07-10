package com.ruoyi.live.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.live.domain.LiveKpiConfig;
import com.ruoyi.live.mapper.LiveKpiConfigMapper;
import com.ruoyi.live.service.ILiveKpiConfigService;

/**
 * KPI 配置 服务实现
 */
@Service
public class LiveKpiConfigServiceImpl implements ILiveKpiConfigService
{
    @Autowired
    private LiveKpiConfigMapper kpiConfigMapper;

    @Override
    public List<LiveKpiConfig> selectKpiConfigList(LiveKpiConfig config)
    {
        return kpiConfigMapper.selectKpiConfigList(config);
    }

    @Override
    public LiveKpiConfig selectKpiConfig(Long streamerId, Integer year, Integer month)
    {
        // 直接查主播专属配置，不使用默认配置
        return kpiConfigMapper.selectKpiConfig(streamerId, year, month);
    }

    @Override
    public int insertKpiConfig(LiveKpiConfig config)
    {
        return kpiConfigMapper.insertKpiConfig(config);
    }

    @Override
    public int updateKpiConfig(LiveKpiConfig config)
    {
        return kpiConfigMapper.updateKpiConfig(config);
    }

    @Override
    public int deleteKpiConfigByIds(Long[] kpiIds)
    {
        return kpiConfigMapper.deleteKpiConfigByIds(kpiIds);
    }
}
