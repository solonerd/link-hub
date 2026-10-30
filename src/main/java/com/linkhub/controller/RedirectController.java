package com.linkhub.controller;

import com.linkhub.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 公开跳转入口：{@code GET /redirect/{shortCode}} → 302 或 404。
 * 限流由 {@code RateLimitInterceptor} 拦截。
 */
@Slf4j
@RestController
@RequestMapping("/redirect")
@RequiredArgsConstructor
public class RedirectController {

    private final ShortLinkService shortLinkService;

    @GetMapping("/{shortCode}")
    public void redirect(@PathVariable String shortCode, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("收到短链接跳转请求: {}", shortCode);
        var s = shortLinkService.redirectToOriginalUrl(shortCode,request);
        if (s!=null){
            response.sendRedirect(s);
        }else{
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}