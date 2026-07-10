package com.ruoyi.live.domain;

import java.io.Serializable;

/**
 * 每日提交完整性汇总(查询VO,按 主播×日期 聚合)
 */
public class LiveDailySummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 业务日期 yyyy-MM-dd */
    private String bizDate;

    /** 主播ID */
    private Long streamerId;

    /** 主播艺名 */
    private String stageName;

    /** 打赏榜截图张数 */
    private Integer giftCount;

    /** 聊天截图张数 */
    private Integer chatCount;

    /** 汇报条数 */
    private Integer reportCount;

    public String getBizDate()
    {
        return bizDate;
    }

    public void setBizDate(String bizDate)
    {
        this.bizDate = bizDate;
    }

    public Long getStreamerId()
    {
        return streamerId;
    }

    public void setStreamerId(Long streamerId)
    {
        this.streamerId = streamerId;
    }

    public String getStageName()
    {
        return stageName;
    }

    public void setStageName(String stageName)
    {
        this.stageName = stageName;
    }

    public Integer getGiftCount()
    {
        return giftCount;
    }

    public void setGiftCount(Integer giftCount)
    {
        this.giftCount = giftCount;
    }

    public Integer getChatCount()
    {
        return chatCount;
    }

    public void setChatCount(Integer chatCount)
    {
        this.chatCount = chatCount;
    }

    public Integer getReportCount()
    {
        return reportCount;
    }

    public void setReportCount(Integer reportCount)
    {
        this.reportCount = reportCount;
    }
}
