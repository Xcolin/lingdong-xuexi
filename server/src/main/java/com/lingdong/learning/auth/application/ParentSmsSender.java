package com.lingdong.learning.auth.application;

/** 隔离短信供应商，业务层不依赖具体厂商 SDK。 */
@FunctionalInterface
public interface ParentSmsSender {
    void send(String mobile, ParentSmsPurpose purpose, String code);
}
