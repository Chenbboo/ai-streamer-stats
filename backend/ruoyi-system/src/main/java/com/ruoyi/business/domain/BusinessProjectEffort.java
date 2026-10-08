package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 员工按日提交、项目负责人确认的项目实际投入。 */
public class BusinessProjectEffort extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long effortId;
    private Long projectId;
    private Long userId;
    private String userName;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date bizDate;
    private BigDecimal plannedPercent;
    private BigDecimal actualPercent;
    private String sourceType;
    private String reportStatus;
    private String deviationReason;
    private Long confirmedUserId;
    private String confirmedUserName;
    private Date confirmedTime;
    private Integer version;

    public Long getEffortId(){return effortId;} public void setEffortId(Long v){effortId=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getUserName(){return userName;} public void setUserName(String v){userName=v;}
    public Date getBizDate(){return bizDate;} public void setBizDate(Date v){bizDate=v;}
    public BigDecimal getPlannedPercent(){return plannedPercent;} public void setPlannedPercent(BigDecimal v){plannedPercent=v;}
    public BigDecimal getActualPercent(){return actualPercent;} public void setActualPercent(BigDecimal v){actualPercent=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public String getReportStatus(){return reportStatus;} public void setReportStatus(String v){reportStatus=v;}
    public String getDeviationReason(){return deviationReason;} public void setDeviationReason(String v){deviationReason=v;}
    public Long getConfirmedUserId(){return confirmedUserId;} public void setConfirmedUserId(Long v){confirmedUserId=v;}
    public String getConfirmedUserName(){return confirmedUserName;} public void setConfirmedUserName(String v){confirmedUserName=v;}
    public Date getConfirmedTime(){return confirmedTime;} public void setConfirmedTime(Date v){confirmedTime=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
