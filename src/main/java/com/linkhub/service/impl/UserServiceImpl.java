package com.linkhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linkhub.common.MessageConstants;
import com.linkhub.common.Result;
import com.linkhub.common.ResultCode;
import com.linkhub.dto.PasswordUpdateDTO;
import com.linkhub.dto.UserProfileUpdateDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.entity.User;
import com.linkhub.mapper.UserMapper;
import com.linkhub.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 与 AuthServiceImpl 保持一致：按 userId 存储的 Refresh Token */
    private static final String REFRESH_KEY_PREFIX = "refresh:user:";

    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;



    @Override
    public Result<User> getCurrentUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            return Result.error(ResultCode.USER_NOT_EXIST);
        }

        // 脱敏处理：移除密码字段
        user.setPassword(null);
        return Result.success(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updatePassword(Long userId, PasswordUpdateDTO dto) {
        // 1. 查询用户
        User user = this.getById(userId);
        if (user == null) {
            return Result.error(ResultCode.USER_NOT_EXIST);
        }

        // 2. 验证旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            return Result.error(ResultCode.USER_PASSWORD_ERROR);
        }

        // 3. 新密码不能与旧密码相同
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            return Result.error(400, "新密码不能与旧密码相同");
        }

        // 4. 更新密码
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        boolean updated = this.updateById(user);

        if (!updated) {
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
        }

        revokeRefreshTokenAfterCommit(userId);
        log.info("用户密码修改成功，userId: {}", userId);
        return Result.success("密码修改成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateProfile(Long userId, UserProfileUpdateDTO dto) {
        // 1. 查询用户
        User user = this.getById(userId);
        if (user == null) {
            return Result.error(ResultCode.USER_NOT_EXIST);
        }

        // 2. 如果修改邮箱，检查是否已被占用
        if (dto.getEmail() != null && !dto.getEmail().equals(user.getEmail())) {
            boolean emailExists = this.lambdaQuery()
                    .eq(User::getEmail, dto.getEmail())
                    .ne(User::getUserId, userId)  // 排除自己
                    .exists();
            if (emailExists) {
                return Result.error(ResultCode.USER_EMAIL_REGISTERED);
            }
            user.setEmail(dto.getEmail());
        }

        // 3. 如果修改用户名，检查是否已被占用
        if (dto.getUsername() != null && !dto.getUsername().equals(user.getUsername())) {
            boolean usernameExists = this.lambdaQuery()
                    .eq(User::getUsername, dto.getUsername())
                    .ne(User::getUserId, userId)
                    .exists();
            if (usernameExists) {
                return Result.error(400, "用户名已被占用");
            }
            user.setUsername(dto.getUsername());
        }

        // 4. 更新手机号
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }

        // 5. 保存更新
        boolean updated = this.updateById(user);
        if (!updated) {
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
        }

        log.info("用户信息修改成功，userId: {}", userId);
        return Result.success(MessageConstants.USER_UPDATE_SUCCESS, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteUser(Long userId) {
        boolean deleted = this.removeById(userId);
        if (!deleted) {
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
        }

        revokeRefreshTokenAfterCommit(userId);
        log.info("用户账号已删除（逻辑删除），userId: {}", userId);
        return Result.success("账号已注销", null);
    }

    /** 改密/销户后吊销 Refresh Token：有事务则提交后再删，否则立即删 */
    private void revokeRefreshTokenAfterCommit(Long userId) {
        String key = REFRESH_KEY_PREFIX + userId;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            stringRedisTemplate.delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stringRedisTemplate.delete(key);
            }
        });
    }
}
