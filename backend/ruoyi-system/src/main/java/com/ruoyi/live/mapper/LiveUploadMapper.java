package com.ruoyi.live.mapper;

import java.util.List;
import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.live.domain.LiveDailySummary;
import com.ruoyi.live.domain.LiveUpload;

/**
 * 上传记录 数据层
 */
public interface LiveUploadMapper
{
    public List<LiveUpload> selectLiveUploadList(LiveUpload upload);

    public LiveUpload selectLiveUploadById(Long uploadId);

    public List<LiveDailySummary> selectDailySummary(LiveUpload upload);

    public int insertLiveUpload(LiveUpload upload);

    public int deleteLiveUploadByIds(Long[] uploadIds);

    public List<LiveUpload> selectLiveUploadByIds(Long[] uploadIds);

    public int updateAiResult(@Param("uploadId") Long uploadId,
                              @Param("aiStatus") String aiStatus,
                              @Param("aiResult") String aiResult);

    public int updateBizDateById(@Param("uploadId") Long uploadId, @Param("bizDate") Date bizDate);

    public int countDailyReportConflict(@Param("streamerId") Long streamerId,
                                        @Param("uploadId") Long uploadId,
                                        @Param("bizDate") Date bizDate);

    public int updateDailyReportBizDateByUploadId(@Param("uploadId") Long uploadId,
                                                  @Param("bizDate") Date bizDate);

    public int insertCustomerIfAbsent(@Param("nickname") String nickname,
                                      @Param("badge") String badge,
                                      @Param("upload") LiveUpload upload);

    public Long selectCustomerIdByNickname(@Param("nickname") String nickname, @Param("streamerId") Long streamerId);

    public int upsertGiftRecord(@Param("upload") LiveUpload upload,
                                @Param("customerId") Long customerId,
                                @Param("rankNo") Integer rankNo,
                                @Param("xu") Integer xu);

    public int upsertChatContact(@Param("upload") LiveUpload upload,
                                 @Param("customerId") Long customerId,
                                 @Param("hasInteraction") Integer hasInteraction);

    public int upsertDailyReport(@Param("upload") LiveUpload upload,
                                @Param("totalXu") Integer totalXu,
                                @Param("rawText") String rawText);
}
