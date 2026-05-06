package org.ost.advertisement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.config.CleanupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CleanupProperties.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Application {
    static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }
}

