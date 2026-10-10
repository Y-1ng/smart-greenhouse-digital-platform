package com.smartgreenhouse.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 业务服务启动入口，负责环境、控制、溯源和治理接口。
 */
@SpringBootApplication
public class SmartGreenhouseApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartGreenhouseApiApplication.class, args);
    }
}
