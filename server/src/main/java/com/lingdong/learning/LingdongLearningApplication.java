package com.lingdong.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LingdongLearningApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingdongLearningApplication.class, args);
    }
}
