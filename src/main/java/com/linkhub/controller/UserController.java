package com.linkhub.controller;

import com.linkhub.common.Result;
import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.dto.PasswordUpdateDTO;
import com.linkhub.dto.UserProfileUpdateDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.entity.User;
import com.linkhub.service.UserService;
import com.linkhub.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Tag(name = "用户模块", description = "用户信息管理")
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public Result<User> getCurrentUserInfo() {
        Long userId = getCurrentUserId();
        return userService.getCurrentUserInfo(userId);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        Long userId = getCurrentUserId();
        return userService.updatePassword(userId, dto);
    }

    /**
     * 修改个人信息
     */
    @PutMapping("/profile")
    @Operation(summary = "修改个人信息")
    public Result<Void> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        Long userId = getCurrentUserId();
        return userService.updateProfile(userId, dto);
    }

    /**
     * 注销账号（逻辑删除）
     */
    @DeleteMapping("/delete")
    @Operation(summary = "注销账号")
    public Result<Void> deleteUser() {
        Long userId = getCurrentUserId();
        return userService.deleteUser(userId);
    }

    /**
     * 【管理员】获取所有用户列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有用户列表（管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> listUsers() {
        List<User> users = userService.list();
        users.forEach(user -> user.setPassword(null));
        return Result.success(users);
    }

    /**
     * 从 SecurityContext 中获取当前用户ID
     */
    private Long getCurrentUserId() {
       return SecurityUtil.getCurrentUserId();
    }
}