package com.lingdong.learning.auth.application;

import java.util.List;

public record DeviceSessionPage(List<DeviceSession> items, long total, int page, int pageSize) { }
