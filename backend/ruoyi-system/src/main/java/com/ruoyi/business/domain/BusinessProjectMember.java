package com.ruoyi.business.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectMember extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long memberId;
    private Long projectId;
    private Long userId;
    private String userNameSnapshot;
    private String accountName;
    private String memberRole;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date joinedDate;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date leftDate;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserNameSnapshot() { return userNameSnapshot; }
    public void setUserNameSnapshot(String userNameSnapshot) { this.userNameSnapshot = userNameSnapshot; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getMemberRole() { return memberRole; }
    public void setMemberRole(String memberRole) { this.memberRole = memberRole; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getJoinedDate() { return joinedDate; }
    public void setJoinedDate(Date joinedDate) { this.joinedDate = joinedDate; }
    public Date getLeftDate() { return leftDate; }
    public void setLeftDate(Date leftDate) { this.leftDate = leftDate; }
}
