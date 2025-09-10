package com.example.cubeserver;

import com.example.cubeserver.controller.CubeController;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CubeServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CubeServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider cubeJsTools(CubeController cubeController) {
        return MethodToolCallbackProvider.builder().toolObjects(cubeController).build();
    }
}
