package com.itsm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// jBPM (Kogito) generates its runtime wiring under org.kie.kogito.app and ships Spring
// components under org.kie.kogito.*, so they must be scanned explicitly (see docs/jbpm-spike.md).
@SpringBootApplication(scanBasePackages = {"com.itsm.backend", "org.kie.kogito.**"})
@ConfigurationPropertiesScan("com.itsm.backend")
public class ItsmBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsmBackendApplication.class, args);
    }
}
