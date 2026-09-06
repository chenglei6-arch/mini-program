package com.chenglei.miniprogram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniProgramBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniProgramBackendApplication.class, args);
    }
}
