package com.lingdong.learning.exportjob.application;

/** 固化业务对象及不可变数据上界，执行时仍需重新鉴权。 */
public record ExportScopeSnapshot(Long studentId, long upperBound) { }
