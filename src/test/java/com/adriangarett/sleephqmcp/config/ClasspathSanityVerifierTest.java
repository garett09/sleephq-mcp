package com.adriangarett.sleephqmcp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ClasspathSanityVerifierTest {

    @Test
    void requiredSupportClasses_areOnTestClasspath() {
        assertThatCode(() -> {
            Class.forName("com.adriangarett.sleephqmcp.support.TeamFileResolver");
            Class.forName("com.adriangarett.sleephqmcp.support.BinaryDownloadSupport");
            Class.forName("com.adriangarett.sleephqmcp.support.O2ImportResolver");
            Class.forName("org.springframework.web.method.annotation.ExceptionHandlerMethodResolver");
        }).doesNotThrowAnyException();
    }
}
