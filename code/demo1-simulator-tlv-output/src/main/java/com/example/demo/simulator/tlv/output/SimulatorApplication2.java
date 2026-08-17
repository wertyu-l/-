package com.example.demo.simulator.tlv.output;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TLV 输出设备模拟器入口。
 * <p>
 * 采用 Spring Boot 空壳模式：仅启动 IoC 容器，不启动 Tomcat/SpringMVC。
 * 通过 {@code --spring.profiles.active=8092} 指定端口与发现端口。
 */
@SpringBootApplication
public class SimulatorApplication2 {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SimulatorApplication2.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

}