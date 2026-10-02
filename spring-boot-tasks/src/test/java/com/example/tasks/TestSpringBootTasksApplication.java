package com.example.tasks;

import org.springframework.boot.SpringApplication;

public class TestSpringBootTasksApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringBootTasksApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
