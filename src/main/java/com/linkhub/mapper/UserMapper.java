package com.linkhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linkhub.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 只要继承 BaseMapper，MyBatis-Plus 就会自动把 insert, selectById, update 等方法注入进来
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 如果你有极其复杂的连表查询，再在这里定义方法并在 XML 里写 SQL
}