package com.linkhub.service;

import com.linkhub.common.Result;
import com.linkhub.dto.LoginDTO;
import com.linkhub.dto.RefreshTokenDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.vo.TokenVO;

/** 认证：注册、验证码、登录、刷新与登出。 */
public interface AuthService {

    Result<String> logout(RefreshTokenDTO dto);

    Result<String> register(UserRegisterDTO registerDTO);

    Result<String> sendVerificationCode(String email);

    Result<TokenVO> login(LoginDTO loginDTO);

    Result<TokenVO> refresh(RefreshTokenDTO dto);
}