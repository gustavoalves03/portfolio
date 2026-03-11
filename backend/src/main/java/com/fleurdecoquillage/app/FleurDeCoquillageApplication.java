package com.fleurdecoquillage.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FleurDeCoquillageApplication {

	public static void main(String[] args) {
		SpringApplication.run(FleurDeCoquillageApplication.class, args);
	}

}
