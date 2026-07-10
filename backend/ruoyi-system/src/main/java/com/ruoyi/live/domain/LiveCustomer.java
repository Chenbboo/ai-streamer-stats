package com.ruoyi.live.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

public class LiveCustomer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long customerId;
    private Long streamerId;
    private String nickname;
    private String profileUrl;
    private String avatarPath;
    private String badge;
    private Long mergedIntoId;
    private Date firstSeenDate;
    private Date lastSeenDate;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getStreamerId() { return streamerId; }
    public void setStreamerId(Long streamerId) { this.streamerId = streamerId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getProfileUrl() { return profileUrl; }
    public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }

    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public Long getMergedIntoId() { return mergedIntoId; }
    public void setMergedIntoId(Long mergedIntoId) { this.mergedIntoId = mergedIntoId; }

    public Date getFirstSeenDate() { return firstSeenDate; }
    public void setFirstSeenDate(Date firstSeenDate) { this.firstSeenDate = firstSeenDate; }

    public Date getLastSeenDate() { return lastSeenDate; }
    public void setLastSeenDate(Date lastSeenDate) { this.lastSeenDate = lastSeenDate; }
}
