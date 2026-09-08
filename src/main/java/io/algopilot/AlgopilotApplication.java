package io.algopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlgopilotApplication {
  public static void main(String[] args) { SpringApplication.run(AlgopilotApplication.class, args); }
}
