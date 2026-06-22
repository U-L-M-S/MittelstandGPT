package com.mittelstandgpt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the MittelstandGPT backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MittelstandGptApplication {

    public static void main(String[] args) {
        SpringApplication.run(MittelstandGptApplication.class, args);
    }
}
