package com.linkhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linkhub.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 短链接 Mapper
 * 继承 BaseMapper 获得基础的 CRUD 方法
 */
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    /**
     * 增加点击量（原子操作）
     * 使用 @Update 注解方式，不需要 XML 文件
     * 在高并发场景下保证 click_num 准确累加
     *
     * @param shortCode 短链接码
     */
    @Update("UPDATE t_link SET click_num = click_num + 1 WHERE short_code = #{shortCode}")
    void incrementClickNum(@Param("shortCode") String shortCode);

    /**
     * 批量增加点击量（用于定时任务汇总）
     *
     * @param shortCode 短链接码
     * @param count 增加的数量
     * @return 影响的行数
     */
    @Update("UPDATE t_link SET click_num = click_num + #{count} WHERE short_code = #{shortCode}")
    int incrementClickNumByCount(@Param("shortCode") String shortCode, @Param("count") int count);

    @Select("SELECT COALESCE(SUM(click_num), 0) FROM t_link WHERE del_flag = 0")
    Long sumTotalClicks();

    @Select("SELECT COUNT(*) FROM t_link WHERE del_flag = 0 AND user_id = #{userId}")
    Long countByUser(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(click_num), 0) FROM t_link WHERE del_flag = 0 AND user_id = #{userId}")
    Long sumClicksByUser(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_link WHERE del_flag = 0 AND status = 0 AND user_id = #{userId} AND valid_date > NOW()")
    Long countActiveByUser(@Param("userId") Long userId);
}