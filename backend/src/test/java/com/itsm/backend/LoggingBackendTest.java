package com.itsm.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The project logs through log4j2 (fixed decision); Logback must not come back with a new starter. */
class LoggingBackendTest {

    @Test
    void slf4jIsBoundToLog4j2() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .isEqualTo("org.apache.logging.slf4j.Log4jLoggerFactory");
    }

    @Test
    void logbackIsNotOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("ch.qos.logback.classic.LoggerContext"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
