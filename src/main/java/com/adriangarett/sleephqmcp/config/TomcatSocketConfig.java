package com.adriangarett.sleephqmcp.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tomcat's {@link AbstractProtocol} constructor calls {@code setConnectionLinger(-1)}, which sets both
 * {@code soLingerOn=false} and {@code soLingerTime=-1} on {@link SocketProperties}. Tomcat then calls
 * {@link java.net.Socket#setSoLinger(boolean, int)} whenever both fields are non-null; on macOS that
 * throws {@code SocketException: Invalid argument} even when linger is disabled.
 * <p>
 * Nulling SO_LINGER fields on the endpoint {@link SocketProperties} avoids macOS {@code Invalid argument} on accept.
 */
@Configuration
public class TomcatSocketConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatSoLingerMacOsCustomizer() {
        return factory -> factory.addConnectorCustomizers(TomcatSocketConfig::clearSoLingerOnConnector);
    }

    private static void clearSoLingerOnConnector(Connector connector) {
        ProtocolHandler handler = connector.getProtocolHandler();
        if (!(handler instanceof AbstractProtocol<?> protocol)) {
            return;
        }
        AbstractEndpoint<?, ?> endpoint = getEndpoint(protocol);
        resetSoLingerFields(endpoint.getSocketProperties());
    }

    private static AbstractEndpoint<?, ?> getEndpoint(AbstractProtocol<?> protocol) {
        try {
            Method getEndpoint = AbstractProtocol.class.getDeclaredMethod("getEndpoint");
            getEndpoint.setAccessible(true);
            return (AbstractEndpoint<?, ?>) getEndpoint.invoke(protocol);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access Tomcat protocol endpoint", e);
        }
    }

    private static void resetSoLingerFields(SocketProperties socketProperties) {
        try {
            Field soLingerOn = SocketProperties.class.getDeclaredField("soLingerOn");
            Field soLingerTime = SocketProperties.class.getDeclaredField("soLingerTime");
            soLingerOn.setAccessible(true);
            soLingerTime.setAccessible(true);
            soLingerOn.set(socketProperties, null);
            soLingerTime.set(socketProperties, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reset Tomcat SO_LINGER socket properties", e);
        }
    }
}
