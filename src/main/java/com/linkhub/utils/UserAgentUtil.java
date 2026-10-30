package com.linkhub.utils;

import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;

@Slf4j
public class UserAgentUtil {

    // 单例模式，避免重复初始化（耗费性能）
    private static final UserAgentAnalyzer ANALYZER;

    static {
        try {
            ANALYZER = UserAgentAnalyzer
                    .newBuilder()
                    .withField("DeviceClass")
                    .withField("AgentNameVersion")
                    .withField("AgentName")
                    .withField("AgentVersion")
                    .withField("OperatingSystemName")
                    .withField("OperatingSystemVersion")
                    .build();
        } catch (Exception e) {
            log.error("UserAgentAnalyzer 初始化失败", e);
            throw new RuntimeException("UserAgentAnalyzer 初始化失败", e);
        }
    }

    /**
     * 解析 User-Agent 并返回封装结果
     */
    public static UserAgentInfo parse(String userAgentString) {
        if (userAgentString == null || userAgentString.isEmpty()) {
            return new UserAgentInfo();
        }

        try {
            UserAgent agent = ANALYZER.parse(userAgentString);

            UserAgentInfo info = new UserAgentInfo();
            info.deviceType = agent.getValue("DeviceClass");
            info.browser = agent.getValue("AgentName");
            info.browserVersion = agent.getValue("AgentVersion");
            info.os = agent.getValue("OperatingSystemName");
            info.osVersion = agent.getValue("OperatingSystemVersion");

            // 规范化设备类型
            normalizeDeviceType(info);

            return info;
        } catch (Exception e) {
            log.warn("User-Agent 解析失败: {}", userAgentString, e);
            return new UserAgentInfo();
        }
    }

    /**
     * 将 DeviceClass 映射为标准值
     */
    private static void normalizeDeviceType(UserAgentInfo info) {
        if (info.deviceType == null) {
            info.deviceType = "UNKNOWN";
            return;
        }

        String device = info.deviceType.toLowerCase();
        if (device.contains("phone") || device.contains("mobile")) {
            info.deviceType = "MOBILE";
        } else if (device.contains("tablet") || device.contains("pad")) {
            info.deviceType = "TABLET";
        } else if (device.contains("desktop") || device.contains("computer") || device.contains("pc")) {
            info.deviceType = "PC";
        } else {
            // 进一步判断
            if (info.os != null && (info.os.toLowerCase().contains("android") || info.os.toLowerCase().contains("ios"))) {
                info.deviceType = "MOBILE";
            } else {
                info.deviceType = "UNKNOWN";
            }
        }
    }

    /**
     * User-Agent 解析结果封装类
     */
    public static class UserAgentInfo {
        public String deviceType = "UNKNOWN";
        public String browser = "UNKNOWN";
        public String browserVersion = "UNKNOWN";
        public String os = "UNKNOWN";
        public String osVersion = "UNKNOWN";

        @Override
        public String toString() {
            return "UserAgentInfo{" +
                    "deviceType='" + deviceType + '\'' +
                    ", browser='" + browser + '\'' +
                    ", browserVersion='" + browserVersion + '\'' +
                    ", os='" + os + '\'' +
                    ", osVersion='" + osVersion + '\'' +
                    '}';
        }
    }
}