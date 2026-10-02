package com.example.tasks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBootTasksApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootTasksApplication.class, args);
	}

}
