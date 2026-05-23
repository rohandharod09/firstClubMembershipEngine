package com.firstclub.membership;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MembershipEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MembershipEngineApplication.class, args);
    }
}
