package com.ruoyi.live.mapper;

import java.util.List;
import com.ruoyi.live.domain.LiveStreamer;

/**
 * 主播 数据层
 */
public interface LiveStreamerMapper
{
    public List<LiveStreamer> selectLiveStreamerList(LiveStreamer streamer);

    public LiveStreamer selectLiveStreamerById(Long streamerId);

    public LiveStreamer selectLiveStreamerByUserId(Long userId);

    public int insertLiveStreamer(LiveStreamer streamer);

    public int updateLiveStreamer(LiveStreamer streamer);

    public int deleteLiveStreamerByIds(Long[] streamerIds);
}
