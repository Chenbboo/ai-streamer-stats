package com.ruoyi.live.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 主播信息 live_streamer
 */
public class LiveStreamer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主播ID */
    private Long streamerId;

    /** 关联 sys_user.user_id */
    private Long userId;

    /** TikTok 账号 */
    private String tiktokHandle;

    /** 艺名/汇报用名 */
    private String stageName;

    /** 状态(0在职 1离职) */
    private String status;

    public Long getStreamerId()
    {
        return streamerId;
    }

    public void setStreamerId(Long streamerId)
    {
        this.streamerId = streamerId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTiktokHandle()
    {
        return tiktokHandle;
    }

    public void setTiktokHandle(String tiktokHandle)
    {
        this.tiktokHandle = tiktokHandle;
    }

    public String getStageName()
    {
        return stageName;
    }

    public void setStageName(String stageName)
    {
        this.stageName = stageName;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
