package com.lingdong.learning.auth.infrastructure.security;

/** 学生登录码摘要入库所需的非明文结果。 */
public record StudentLoginCodeDigest(String hash, String salt, String keyVersion) {
}
