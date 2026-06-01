package com.aiopen.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aiopen.platform.**.mapper")
public class AiOpenPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOpenPlatformApplication.class, args);
    }
}
