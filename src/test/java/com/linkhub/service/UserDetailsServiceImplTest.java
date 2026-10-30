package com.linkhub.service;

import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.entity.User;
import com.linkhub.mapper.UserMapper;
import com.linkhub.service.impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** loadUserById：JWT 过滤器与 refresh 按不可变 userId 回查用户。 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("按 userId 加载：返回当前用户名、角色与启用状态")
    void loadUserById_found() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("renamed");
        user.setPassword("hash");
        user.setRole("USER");
        user.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(user);

        UserDetails details = userDetailsService.loadUserById(1L);

        assertEquals(1L, ((CustomUserDetails) details).getUserId());
        assertEquals("renamed", details.getUsername());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
    }

    @Test
    @DisplayName("封禁用户：enabled=false")
    void loadUserById_banned() {
        User user = new User();
        user.setUserId(2L);
        user.setUsername("banned");
        user.setPassword("hash");
        user.setRole("USER");
        user.setStatus(1);
        when(userMapper.selectById(2L)).thenReturn(user);

        assertFalse(userDetailsService.loadUserById(2L).isEnabled());
    }

    @Test
    @DisplayName("用户不存在：抛 UsernameNotFoundException")
    void loadUserById_missing() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserById(99L));
    }
}