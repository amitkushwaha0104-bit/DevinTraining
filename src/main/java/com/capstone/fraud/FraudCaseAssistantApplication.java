package com.capstone.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudCaseAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudCaseAssistantApplication.class, args);
    }
}
