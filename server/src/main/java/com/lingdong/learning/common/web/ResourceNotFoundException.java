package com.lingdong.learning.common.web;

/** 表示受权限保护的资源不存在，保留参数异常继承关系以兼容既有领域调用方。 */
public class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
