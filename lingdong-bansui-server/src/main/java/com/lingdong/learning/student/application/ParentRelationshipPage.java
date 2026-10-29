package com.lingdong.learning.student.application;

import java.util.List;

public record ParentRelationshipPage<T>(List<T> items, long total, int page, int pageSize) { }
