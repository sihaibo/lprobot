package com.lp.robot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@EnableAsync
@MapperScan("com.lp.robot.dextools.dao")
@SpringBootApplication(scanBasePackages = "com.lp.robot")
public class LpRobotApplication {

    public static void main(String[] args) {
        SpringApplication.run(LpRobotApplication.class, args);
    }
}
