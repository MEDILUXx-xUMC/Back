// 백엔드 애플리케이션을 시작한다.
package com.hanbeonman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HanbeonmanApplication {
    public static void main(String[] args) {
        SpringApplication.run(HanbeonmanApplication.class, args);
    }
}
