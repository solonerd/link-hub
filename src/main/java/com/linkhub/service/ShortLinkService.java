package com.linkhub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linkhub.common.Result;
import com.linkhub.dto.ShortLinkCreateDTO;
import com.linkhub.dto.ShortLinkDelDTO;
import com.linkhub.dto.ShortLinkStatusDTO;
import com.linkhub.dto.ShortLinkUpdateDTO;
import com.linkhub.entity.ShortLink;
import com.linkhub.vo.ClickLogVO;
import com.linkhub.vo.ShortLinkStatsVO;
import com.linkhub.vo.UserStatsVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 短链业务接口（MyBatis-Plus {@link IService}）。
 */
public interface ShortLinkService extends IService<ShortLink> {

    /** 短码跳转解析，不存在/过期/封禁返回 null */
    String redirectToOriginalUrl(String shortCode, HttpServletRequest request);

    Result<String> generateShortLink(@Valid ShortLinkCreateDTO createDTO);

    Result<String> delShortLink(@Valid ShortLinkDelDTO delDTO);

    Result<String> delShortLinks(List<ShortLinkDelDTO> ids);

    Result<ShortLinkStatsVO> getLinkStats(String shortCode, int days);

    Result<UserStatsVO> getUserStats();

    Result<List<ShortLink>> listMyLinks();

    Result<List<ClickLogVO>> pageClickLogs(String shortCode, int page, int size);

    /** 管理员封禁/解封 */
    Result<String> updateStatus(ShortLinkStatusDTO dto);
}