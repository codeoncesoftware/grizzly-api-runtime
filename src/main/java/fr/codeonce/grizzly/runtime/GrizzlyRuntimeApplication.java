package fr.codeonce.grizzly.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class, MongoAutoConfiguration.class, SecurityAutoConfiguration.class})
@EnableFeignClients
@EnableCaching
public class GrizzlyRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrizzlyRuntimeApplication.class, args);
    }
}