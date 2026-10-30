package com.linkhub.config.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 自定义 UserDetails
 * 使用 @Getter 而非 @Data，避免自动生成 toString 泄露密码
 */
@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;



    // 注意：这里不需要重写 toString，使用默认的 Object.toString()
    // 或者可以显式重写，但不包含密码
    @Override
    public String toString() {
        return "CustomUserDetails{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", authorities=" + authorities +
                ", enabled=" + enabled +
                '}';
        // 故意不包含 password！
    }
}