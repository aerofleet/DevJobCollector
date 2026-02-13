package kr.itsdev.devjobcollector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DevjobcollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevjobcollectorApplication.class, args);
	}

}
