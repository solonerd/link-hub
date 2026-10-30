package com.linkhub.controller;

import com.linkhub.common.Result;
import com.linkhub.dto.LoginDTO;
import com.linkhub.dto.RefreshTokenDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.service.AuthService;
import com.linkhub.vo.TokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 API：注册、验证码、登录、刷新与登出（无需登录态）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "认证模块", description = "登录、注册、验证码")
public class AuthController {

    private final AuthService authService;


    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<String> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        return authService.register(registerDTO);
    }

    @PostMapping("/code")
    public Result<String> sendVerificationCode(
            @RequestParam
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确") String email) {
        return authService.sendVerificationCode(email);
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return authService.login(loginDTO);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return authService.refresh(dto);
    }

    @PostMapping("/logout")
    public Result<String> logout(@Valid @RequestBody RefreshTokenDTO dto) {
        return authService.logout(dto);
    }
}