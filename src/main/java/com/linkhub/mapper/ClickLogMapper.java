package com.linkhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linkhub.entity.ClickLog;
import com.linkhub.vo.RegionStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClickLogMapper extends BaseMapper<ClickLog> {

    @Select("""
            SELECT province AS name, COUNT(*) AS count
            FROM t_click_log
            WHERE short_code = #{code}
            GROUP BY province
            ORDER BY count DESC
            LIMIT 10
            """)
    List<RegionStatVO> topProvinces(@Param("code") String code);
}