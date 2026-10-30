package com.linkhub.service.impl;

import com.linkhub.service.IpRegionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.DataBlock;
import org.lionsoul.ip2region.DbConfig;
import org.lionsoul.ip2region.DbSearcher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class IpRegionServiceImpl implements IpRegionService {

    private DbSearcher searcher;

    @PostConstruct
    public void init() {
        try (InputStream in = getClass().getResourceAsStream("/ip2region/ip2region.db")) {
            if (in == null) {
                log.warn("未找到 ip2region.db，地域将显示为未知");
                return;
            }
            Path tmp = Files.createTempFile("ip2region", ".db");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            searcher = new DbSearcher(new DbConfig(), tmp.toString());
        } catch (Exception e) {
            log.warn("ip2region 初始化失败，地域将显示为未知", e);
        }
    }

    @Override
    public String[] locate(String ip) {
        String[] unknown = {"未知", "未知", "未知"};
        if (ip == null || ip.isBlank() || searcher == null) return unknown;
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
            return new String[]{"内网", "内网", "内网"};
        }
        if (ip.contains(":")) return unknown;
        try {
            DataBlock block = searcher.memorySearch(ip);
            if (block == null || block.getRegion() == null) return unknown;
            String[] p = block.getRegion().split("\\|");
            return new String[]{clean(p, 0), clean(p, 2), clean(p, 3)};
        } catch (Exception e) {
            return unknown;
        }
    }

    private static String clean(String[] p, int i) {
        if (i >= p.length || p[i] == null || p[i].isBlank() || "0".equals(p[i])) return "未知";
        return p[i];
    }
}