package com.linkhub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linkhub.common.Result;
import com.linkhub.dto.PasswordUpdateDTO;
import com.linkhub.dto.UserProfileUpdateDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.entity.User;

/**
 * 继承 IService，获得更丰富的业务级方法（如 saveBatch, getOne 等）
 */
public interface UserService extends IService<User> {
    // 这里定义你自定义的业务方法，比如：
    // String login(String username, String password);
    /**
     * 获取当前登录用户信息
     */
    Result<User> getCurrentUserInfo(Long userId);

    /**
     * 修改密码
     */
    Result<Void> updatePassword(Long userId, PasswordUpdateDTO dto);

    /**
     * 修改个人信息
     */
    Result<Void> updateProfile(Long userId, UserProfileUpdateDTO dto);

    /**
     * 软删除用户（逻辑删除）
     */
    Result<Void> deleteUser(Long userId);
}