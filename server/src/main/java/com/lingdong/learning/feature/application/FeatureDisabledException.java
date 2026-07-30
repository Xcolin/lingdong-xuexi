package com.lingdong.learning.feature.application;
/** Raised before a stopped feature can reach its business operation. */
public class FeatureDisabledException extends RuntimeException { public FeatureDisabledException(String code) { super("功能暂未开放：" + code); } }
