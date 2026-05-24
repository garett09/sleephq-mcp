package com.adriangarett.sleephqmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableAspectJAutoProxy
public class SleephqMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SleephqMcpApplication.class, args);
    }

}
