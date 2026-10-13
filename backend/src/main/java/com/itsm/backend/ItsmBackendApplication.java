package com.itsm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// jBPM (Kogito) generates its runtime wiring under org.kie.kogito.app and ships Spring
// components under org.kie.kogito.*, so they must be scanned explicitly (see docs/jbpm-spike.md).
// The JPA user task storage add-on is a configuration in org.jbpm.usertask.jpa.springboot; it declares an
// @EntityScan for its own entities, so ours must be declared too (the packages are merged).
@SpringBootApplication(scanBasePackages = {"com.itsm.backend", "org.kie.kogito.**", "org.jbpm.usertask.jpa.springboot"})
@EntityScan("com.itsm.backend")
@ConfigurationPropertiesScan("com.itsm.backend")
public class ItsmBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsmBackendApplication.class, args);
    }
}
