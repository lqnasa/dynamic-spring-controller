package com.coder.lee.dynamicspringcontroller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.oas.annotations.EnableOpenApi;

@EnableOpenApi
@SpringBootApplication
public class DynamicSpringControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicSpringControllerApplication.class, args);
    }

}
