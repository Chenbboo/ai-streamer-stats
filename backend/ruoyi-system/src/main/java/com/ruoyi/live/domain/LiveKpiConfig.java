package com.ruoyi.live.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * KPI 配置对象 live_kpi_config
 */
public class LiveKpiConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** KPI ID */
    private Long kpiId;

    /** 主播ID(NULL表示默认配置) */
    @Excel(name = "主播ID")
    private Long streamerId;

    /** 年份 */
    @Excel(name = "年份")
    private Integer kpiYear;

    /** 月份 */
    @Excel(name = "月份")
    private Integer kpiMonth;

    /** 送礼日KPI */
    @Excel(name = "送礼日KPI")
    private Integer giftDaily;

    /** 送礼月KPI */
    @Excel(name = "送礼月KPI")
    private Integer giftMonthly;

    /** 新增粉丝日KPI */
    @Excel(name = "新增粉丝日KPI")
    private Integer newFanDaily;

    /** 新增粉丝月KPI */
    @Excel(name = "新增粉丝月KPI")
    private Integer newFanMonthly;

    /** 新增互动日KPI */
    @Excel(name = "新增互动日KPI")
    private Integer chatDaily;

    /** 新增互动月KPI */
    @Excel(name = "新增互动月KPI")
    private Integer chatMonthly;

    /** 新增打赏日KPI(Xu) */
    @Excel(name = "新增打赏日KPI")
    private Integer newTipDaily;

    /** 新增打赏月KPI(Xu) */
    @Excel(name = "新增打赏月KPI")
    private Integer newTipMonthly;

    public void setKpiId(Long kpiId)
    {
        this.kpiId = kpiId;
    }

    public Long getKpiId()
    {
        return kpiId;
    }

    public void setStreamerId(Long streamerId)
    {
        this.streamerId = streamerId;
    }

    public Long getStreamerId()
    {
        return streamerId;
    }

    public void setKpiYear(Integer kpiYear)
    {
        this.kpiYear = kpiYear;
    }

    public Integer getKpiYear()
    {
        return kpiYear;
    }

    public void setKpiMonth(Integer kpiMonth)
    {
        this.kpiMonth = kpiMonth;
    }

    public Integer getKpiMonth()
    {
        return kpiMonth;
    }

    public void setGiftDaily(Integer giftDaily)
    {
        this.giftDaily = giftDaily;
    }

    public Integer getGiftDaily()
    {
        return giftDaily;
    }

    public void setGiftMonthly(Integer giftMonthly)
    {
        this.giftMonthly = giftMonthly;
    }

    public Integer getGiftMonthly()
    {
        return giftMonthly;
    }

    public void setNewFanDaily(Integer newFanDaily)
    {
        this.newFanDaily = newFanDaily;
    }

    public Integer getNewFanDaily()
    {
        return newFanDaily;
    }

    public void setNewFanMonthly(Integer newFanMonthly)
    {
        this.newFanMonthly = newFanMonthly;
    }

    public Integer getNewFanMonthly()
    {
        return newFanMonthly;
    }

    public void setChatDaily(Integer chatDaily)
    {
        this.chatDaily = chatDaily;
    }

    public Integer getChatDaily()
    {
        return chatDaily;
    }

    public void setChatMonthly(Integer chatMonthly)
    {
        this.chatMonthly = chatMonthly;
    }

    public Integer getChatMonthly()
    {
        return chatMonthly;
    }

    public void setNewTipDaily(Integer newTipDaily)
    {
        this.newTipDaily = newTipDaily;
    }

    public Integer getNewTipDaily()
    {
        return newTipDaily;
    }

    public void setNewTipMonthly(Integer newTipMonthly)
    {
        this.newTipMonthly = newTipMonthly;
    }

    public Integer getNewTipMonthly()
    {
        return newTipMonthly;
    }

    @Override
    public String toString()
    {
        return "LiveKpiConfig{" +
                "kpiId=" + kpiId +
                ", streamerId=" + streamerId +
                ", kpiYear=" + kpiYear +
                ", kpiMonth=" + kpiMonth +
                ", giftDaily=" + giftDaily +
                ", giftMonthly=" + giftMonthly +
                ", newFanDaily=" + newFanDaily +
                ", newFanMonthly=" + newFanMonthly +
                ", chatDaily=" + chatDaily +
                ", chatMonthly=" + chatMonthly +
                ", newTipDaily=" + newTipDaily +
                ", newTipMonthly=" + newTipMonthly +
                "}";
    }
}
