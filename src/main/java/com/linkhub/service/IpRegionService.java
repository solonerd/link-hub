package com.linkhub.service;

public interface IpRegionService {
    /** 返回 [国家, 省, 市] */
    String[] locate(String ip);
}