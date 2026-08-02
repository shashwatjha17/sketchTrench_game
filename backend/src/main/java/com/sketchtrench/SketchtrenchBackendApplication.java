package com.sketchtrench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. {@code @ConfigurationPropertiesScan} makes every
 * {@code @ConfigurationProperties} class in the package tree a candidate for binding
 * (e.g. {@code app.jwt.*} -> JwtProperties). {@code @EnableScheduling} powers the
 * scheduled maintenance tasks (token purge, etc.).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SketchtrenchBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SketchtrenchBackendApplication.class, args);
    }

}
