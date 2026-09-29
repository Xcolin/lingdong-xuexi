package com.lingdong.learning.interfaceconfig.domain;

/** Defines the business boundary granted to an interface service. */
public enum InterfaceAuthorizationScope {
    GLOBAL,
    REGION,
    SCHOOL,
    INSTITUTION,
    SPECIFIED_CALLER
}
