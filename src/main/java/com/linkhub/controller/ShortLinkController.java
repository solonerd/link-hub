package com.linkhub.controller;

import com.linkhub.common.Result;
import com.linkhub.dto.ShortLinkCreateDTO;
import com.linkhub.dto.ShortLinkDelDTO;
import com.linkhub.dto.ShortLinkStatusDTO;
import com.linkhub.entity.ShortLink;
import com.linkhub.service.ShortLinkService;
import com.linkhub.vo.ClickLogVO;
import com.linkhub.vo.ShortLinkStatsVO;
import com.linkhub.vo.UserStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短链管理 API：生成、删除、列表、统计；管理员可封禁/解封。
 */
@RestController
@RequestMapping("/link")
@RequiredArgsConstructor
@Tag(name = "短链接管理", description = "短链接的增删改查和统计")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/generate")
    @Operation(summary = "生成短链接")
    public Result<String> generateShortLink(@Valid @RequestBody ShortLinkCreateDTO createDTO) {
        return shortLinkService.generateShortLink(createDTO);
    }
    @DeleteMapping("/del")
    @Operation(summary = "delete a link")
    public Result<String> delShortLink(@Valid @RequestBody ShortLinkDelDTO delDTO) {
        return shortLinkService.delShortLink(delDTO);
    }

    @DeleteMapping("/dels")
    @Operation(summary = "delete some links")
    public Result<String> delShortLinks(@Valid @RequestBody List<ShortLinkDelDTO> ids) {
        return shortLinkService.delShortLinks(ids);
    }

    @GetMapping("/overview")
    @Operation(summary = "我的短链概览")
    public Result<UserStatsVO> overview() {
        return shortLinkService.getUserStats();
    }

    @GetMapping("/list")
    @Operation(summary = "我的短链列表")
    public Result<List<ShortLink>> listMyLinks() {
        return shortLinkService.listMyLinks();
    }


    @GetMapping("/stats/{shortCode}")
    @Operation(summary = "单条短链统计")
    public Result<ShortLinkStatsVO> stats(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "7") int days) {
        return shortLinkService.getLinkStats(shortCode, days);
    }

    @GetMapping("/stats/{shortCode}/logs")
    @Operation(summary = "短链点击明细")
    public Result<List<ClickLogVO>> logs(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shortLinkService.pageClickLogs(shortCode, page, size);
    }

    @PutMapping("/admin/status")
    @Operation(summary = "封禁/解封短链（管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateStatus(@Valid @RequestBody ShortLinkStatusDTO dto) {
        return shortLinkService.updateStatus(dto);
    }

}