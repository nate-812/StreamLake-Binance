package com.streamlake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StreamLakeApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamLakeApplication.class, args);
    }
}
