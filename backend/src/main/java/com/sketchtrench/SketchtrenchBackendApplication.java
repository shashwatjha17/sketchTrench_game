package com.sketchtrench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Guest-only: sessions, rooms and games all live in memory on one instance.
 */
@SpringBootApplication
public class SketchtrenchBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SketchtrenchBackendApplication.class, args);
    }

}
