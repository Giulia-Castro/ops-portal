package io.github.giuliacastro.opsportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpsPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsPortalApplication.class, args);
    }
}
