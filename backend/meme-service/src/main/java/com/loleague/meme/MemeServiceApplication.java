package com.loleague.meme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.loleague")
@ConfigurationPropertiesScan
@EnableScheduling
public class MemeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemeServiceApplication.class, args);
    }
}
