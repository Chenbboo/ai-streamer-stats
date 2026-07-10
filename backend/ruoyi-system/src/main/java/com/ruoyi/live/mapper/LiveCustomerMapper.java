package com.ruoyi.live.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.live.domain.LiveCustomer;

public interface LiveCustomerMapper
{
    List<LiveCustomer> selectCustomerList(LiveCustomer customer);

    LiveCustomer selectCustomerById(Long customerId);

    LiveCustomer selectCustomerByNicknameAndStreamer(@Param("nickname") String nickname, @Param("streamerId") Long streamerId);

    int updateGiftCustomerId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int updateChatCustomerId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int markCustomerMerged(@Param("oldId") Long oldId, @Param("newId") Long newId);

    int insertAlias(@Param("customerId") Long customerId, @Param("nickname") String nickname, @Param("sourceType") String sourceType);

    int deleteCustomerById(Long customerId);
}
