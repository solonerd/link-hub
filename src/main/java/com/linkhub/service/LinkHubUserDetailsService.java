package com.linkhub.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * 扩展 Spring Security 的 UserDetailsService，支持按不可变主键 userId 加载用户。
 */
public interface LinkHubUserDetailsService extends UserDetailsService {

    /**
     * 按主键 userId 加载用户（用于 JWT 鉴权与 Refresh 刷新）
     */
    UserDetails loadUserById(Long userId) throws UsernameNotFoundException;
}