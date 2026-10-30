package com.linkhub.utils;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.DataBlock;
import org.lionsoul.ip2region.DbConfig;
import org.lionsoul.ip2region.DbSearcher;
import org.springframework.core.io.ClassPathResource;

@Slf4j
public class GeoIpUtil {

    private static DbSearcher searcher;

    static {
        try {
            // 读取 classpath 下的 ip2region.db 文件
            ClassPathResource resource = new ClassPathResource("ip2region/ip2region.db");
            String dbPath = resource.getFile().getAbsolutePath();

            // 初始化 DbSearcher
            DbConfig config = new DbConfig();
            searcher = new DbSearcher(config, dbPath);

            log.info("GeoIpUtil 初始化成功，使用 ip2region.db");
        } catch (Exception e) {
            log.error("GeoIpUtil 初始化失败，IP 归属地将不可用", e);
        }
    }

    public static GeoInfo query(String ip) {
        GeoInfo info = new GeoInfo();
        info.country = "未知";
        info.province = "未知";
        info.city = "未知";

        if (searcher == null || ip == null || ip.isEmpty()) {
            return info;
        }

        if (isLocalIp(ip)) {
            info.country = "本地";
            info.province = "本地";
            info.city = "本地";
            return info;
        }

        try {
            // 🔥 .db 格式用 memorySearch
            DataBlock dataBlock = searcher.memorySearch(ip);
            String region = dataBlock.getRegion();
            String[] parts = region.split("\\|");
            // 格式: 国家|区域|省份|城市|ISP
            if (parts.length >= 5) {
                info.country = "0".equals(parts[0]) ? "未知" : parts[0];
                info.province = "0".equals(parts[2]) ? "未知" : parts[2];
                info.city = "0".equals(parts[3]) ? "未知" : parts[3];
            }
        } catch (Exception e) {
            log.debug("IP 归属地查询失败: {}，错误: {}", ip, e.getMessage());
        }

        return info;
    }

    private static boolean isLocalIp(String ip) {
        return ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.") ||
                ip.startsWith("172.16.") || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    public static class GeoInfo {
        public String country;
        public String province;
        public String city;
    }
}