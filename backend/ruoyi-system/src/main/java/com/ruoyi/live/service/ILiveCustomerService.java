package com.ruoyi.live.service;

import java.util.List;
import com.ruoyi.live.domain.LiveCustomer;

public interface ILiveCustomerService
{
    List<LiveCustomer> selectCustomerList(LiveCustomer customer);

    LiveCustomer selectCustomerById(Long customerId);

    void mergeCustomers(Long primaryId, Long secondaryId);
}
