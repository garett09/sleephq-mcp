package com.adriangarett.sleephqmcp.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * Uniform structured log line per MCP invocation. One place — applies to every
 * {@code @McpTool}, {@code @McpResource}, {@code @McpPrompt} method automatically.
 */
@Aspect
@Component
public class McpInvocationLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger("mcp.invocation");

    @Around("@annotation(org.springaicommunity.mcp.annotation.McpTool) "
            + "|| @annotation(org.springaicommunity.mcp.annotation.McpResource) "
            + "|| @annotation(org.springaicommunity.mcp.annotation.McpPrompt)")
    public Object logInvocation(ProceedingJoinPoint pjp) throws Throwable {
        String primitive = primitiveKind(pjp);
        String name = primitiveName(pjp);
        int argCount = pjp.getArgs() == null ? 0 : pjp.getArgs().length;
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("mcp {} '{}' ok argc={} latency_ms={}", primitive, name, argCount, ms);
            return result;
        } catch (Throwable t) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("mcp {} '{}' FAIL argc={} latency_ms={} error={}",
                    primitive, name, argCount, ms, t.toString());
            throw t;
        }
    }

    private String primitiveKind(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        if (signature.getMethod().isAnnotationPresent(McpTool.class)) return "tool";
        if (signature.getMethod().isAnnotationPresent(McpResource.class)) return "resource";
        if (signature.getMethod().isAnnotationPresent(McpPrompt.class)) return "prompt";
        return "unknown";
    }

    private String primitiveName(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        McpTool tool = signature.getMethod().getAnnotation(McpTool.class);
        if (tool != null && !tool.name().isBlank()) return tool.name();
        McpResource resource = signature.getMethod().getAnnotation(McpResource.class);
        if (resource != null && !resource.name().isBlank()) return resource.name();
        McpPrompt prompt = signature.getMethod().getAnnotation(McpPrompt.class);
        if (prompt != null && !prompt.name().isBlank()) return prompt.name();
        return signature.getMethod().getName();
    }
}
