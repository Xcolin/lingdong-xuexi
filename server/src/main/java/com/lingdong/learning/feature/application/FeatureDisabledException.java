package com.lingdong.learning.feature.application;
/** 停用功能进入业务操作前抛出的统一异常。 */
public class FeatureDisabledException extends RuntimeException { public FeatureDisabledException(String code) { super("功能暂未开放：" + code); } }
