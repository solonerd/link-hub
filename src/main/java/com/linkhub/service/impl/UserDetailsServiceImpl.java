package com.linkhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.entity.User;
import com.linkhub.mapper.UserMapper;
import com.linkhub.service.LinkHubUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements LinkHubUserDetailsService {

    private final UserMapper userMapper;

    /** 登录认证时由 DaoAuthenticationProvider 调用 */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            log.warn("登录失败，找不到该用户: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return buildUserDetails(user);
    }

    /** JWT 过滤器与 Token 刷新时调用（以不可变的 userId 为锚点） */
    @Override
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("Token 校验失败，找不到该用户 ID: {}", userId);
            throw new UsernameNotFoundException("用户不存在: id=" + userId);
        }
        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(User user) {
        boolean enabled = Integer.valueOf(0).equals(user.getStatus());
        return new CustomUserDetails(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())),
                enabled,
                true,
                true,
                true
        );
    }
}