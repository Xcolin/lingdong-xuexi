package com.lingdong.learning.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Enables Spring cache interception; the active profile selects the concrete cache manager. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfiguration {
}
