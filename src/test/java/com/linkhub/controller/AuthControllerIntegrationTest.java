package com.linkhub.controller;

import com.linkhub.dto.LoginDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)  // 启动随机端口
@ActiveProfiles("test")  // 使用 test 环境配置（独立数据库）
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;  // 模拟 HTTP 请求

    @Autowired
    private UserMapper userMapper;


}