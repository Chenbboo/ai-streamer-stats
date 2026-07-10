package com.ruoyi.live.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.live.domain.LiveKpiConfig;

/**
 * KPI 配置 数据层
 */
public interface LiveKpiConfigMapper
{
    /**
     * 查询 KPI 配置
     */
    public List<LiveKpiConfig> selectKpiConfigList(LiveKpiConfig config);

    /**
     * 查询指定主播指定月份的 KPI 配置
     */
    public LiveKpiConfig selectKpiConfig(@Param("streamerId") Long streamerId,
                                         @Param("year") Integer year,
                                         @Param("month") Integer month);

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
