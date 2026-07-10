package com.ruoyi.live.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.live.domain.LiveCustomer;
import com.ruoyi.live.mapper.LiveCustomerMapper;
import com.ruoyi.live.service.ILiveCustomerService;

@Service
public class LiveCustomerServiceImpl implements ILiveCustomerService
{
    @Autowired
    private LiveCustomerMapper customerMapper;

    @Override
    public List<LiveCustomer> selectCustomerList(LiveCustomer customer)
    {
        return customerMapper.selectCustomerList(customer);
    }

    @Override
    public LiveCustomer selectCustomerById(Long customerId)
    {
        return customerMapper.selectCustomerById(customerId);
    }

    @Override
    @Transactional
    public void mergeCustomers(Long primaryId, Long secondaryId)
    {
        if (primaryId.equals(secondaryId))
        {
            throw new ServiceException("不能合并同一个客户");
        }
        LiveCustomer primary = customerMapper.selectCustomerById(primaryId);
        LiveCustomer secondary = customerMapper.selectCustomerById(secondaryId);
        if (primary == null || secondary == null)
        {
            throw new ServiceException("客户不存在");
        }
        // Save secondary's nickname as alias before merge
        customerMapper.insertAlias(primaryId, secondary.getNickname(), "merge");
        // Move all records from secondary to primary
        customerMapper.updateGiftCustomerId(secondaryId, primaryId);
        customerMapper.updateChatCustomerId(secondaryId, primaryId);
        customerMapper.markCustomerMerged(secondaryId, primaryId);
    }
}
