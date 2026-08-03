package com.lingdong.learning.iam.application;

import com.lingdong.learning.user.domain.User;

import java.util.List;

/** 用户目录分页结果，不包含密码散列或设备会话信息。 */
public record UserDirectoryPage(List<User> items, int page, int pageSize, long total) { }
