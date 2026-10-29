package com.lingdong.learning.auth.application;

import java.util.List;

public record AccountSecurityEventPage(List<AccountSecurityEventView> items, long total,
        int page, int pageSize, long unreadWarningTotal) { }
