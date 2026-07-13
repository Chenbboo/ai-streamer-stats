package com.ruoyi.live.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 上传记录 live_upload
 */
public class LiveUpload extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 上传类型:打赏榜截图 */
    public static final String TYPE_GIFT = "1";
    /** 上传类型:聊天截图 */
    public static final String TYPE_CHAT = "2";
    /** 上传类型:汇报文本 */
    public static final String TYPE_REPORT = "3";
    /** 上传类型:关注截图 */
    public static final String TYPE_FOLLOW = "4";

    /** 上传ID */
    private Long uploadId;

    /** 业务日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date bizDate;

    /** 主播ID */
    private Long streamerId;

    /** 主播艺名(联查显示用) */
    private String stageName;

    /** 类型(1打赏榜截图 2聊天截图 3汇报文本 4关注截图) */
    private String uploadType;

    /** 文件路径 */
    private String filePath;

    /** 汇报原文 */
    private String rawText;

    /** AI识别状态(0待识别 1已识别 2已校正入库 3识别失败) */
    private String aiStatus;

    /** AI识别原始JSON */
    private String aiResult;

    /** 上传人 user_id */
    private Long uploadBy;

    /** 上传人名称(联查显示用) */
    private String uploadByName;

    /** 查询用:开始日期 */
    private String beginDate;

    /** 查询用:结束日期 */
    private String endDate;

    public Long getUploadId()
    {
        return uploadId;
    }

    public void setUploadId(Long uploadId)
    {
        this.uploadId = uploadId;
    }

    public Date getBizDate()
    {
        return bizDate;
    }

    public void setBizDate(Date bizDate)
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

    public String getUploadType()
    {
        return uploadType;
    }

    public void setUploadType(String uploadType)
    {
        this.uploadType = uploadType;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String getRawText()
    {
        return rawText;
    }

    public void setRawText(String rawText)
    {
        this.rawText = rawText;
    }

    public String getAiStatus()
    {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus)
    {
        this.aiStatus = aiStatus;
    }

    public String getAiResult()
    {
        return aiResult;
    }

    public void setAiResult(String aiResult)
    {
        this.aiResult = aiResult;
    }

    public Long getUploadBy()
    {
        return uploadBy;
    }

    public void setUploadBy(Long uploadBy)
    {
        this.uploadBy = uploadBy;
    }

    public String getUploadByName()
    {
        return uploadByName;
    }

    public void setUploadByName(String uploadByName)
    {
        this.uploadByName = uploadByName;
    }

    public String getBeginDate()
    {
        return beginDate;
    }

    public void setBeginDate(String beginDate)
    {
        this.beginDate = beginDate;
    }

    public String getEndDate()
    {
        return endDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }
}
