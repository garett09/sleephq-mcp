package com.adriangarett.sleephqmcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fail fast at startup if support classes or Spring MVC are not loadable in the running JVM.
 * Prevents opaque {@link NoClassDefFoundError} on first MCP tool call after a stale process.
 */
@Component
@Order(0)
public class ClasspathSanityVerifier implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSanityVerifier.class);

    private static final String[] REQUIRED = {
            "com.adriangarett.sleephqmcp.support.TeamFileResolver",
            "com.adriangarett.sleephqmcp.support.BinaryDownloadSupport",
            "com.adriangarett.sleephqmcp.support.O2ImportResolver",
            "org.springframework.web.method.annotation.ExceptionHandlerMethodResolver",
    };

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String className : REQUIRED) {
            try {
                Class.forName(className, true, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                String location = event.getApplicationContext().getClassLoader().toString();
                throw new IllegalStateException(
                        "Classpath incomplete for " + className
                                + ". Stop the server and restart with ./run.sh after mvn package. Loader: "
                                + location,
                        e);
            }
        }
        log.info(
                "Classpath sanity OK (EDF/O2 resolvers + Spring MVC). If tools later fail with NoClassDefFoundError, "
                        + "fully restart ./run.sh — do not hot-reload.");
    }
}
