package com.ruoyi.live.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.live.domain.LiveCustomer;

public interface LiveCustomerMapper
{
    List<LiveCustomer> selectCustomerList(LiveCustomer customer);

    LiveCustomer selectCustomerById(Long customerId);

    LiveCustomer selectCustomerByNicknameAndStreamer(@Param("nickname") String nickname, @Param("streamerId") Long streamerId);

    int mergeGiftRecords(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int deleteGiftRecords(Long customerId);

    int mergeChatContacts(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int deleteChatContacts(Long customerId);

    int mergeFollowRecords(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int deleteFollowRecords(Long customerId);

    int updateChatMessageCustomerId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int moveAliases(@Param("oldId") Long oldId, @Param("newId") Long newId,
                    @Param("streamerId") Long streamerId);

    int mergeCustomerMetadata(@Param("primaryId") Long primaryId, @Param("secondaryId") Long secondaryId);

    int markCustomerMerged(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int insertAlias(@Param("customerId") Long customerId, @Param("streamerId") Long streamerId,
                    @Param("nickname") String nickname, @Param("sourceType") String sourceType,
                    @Param("firstSeenDate") java.util.Date firstSeenDate,
                    @Param("lastSeenDate") java.util.Date lastSeenDate);

    int deleteCustomerById(Long customerId);
}
