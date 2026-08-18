package com.b2chat.ordermanagement.r2dbc.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

import java.io.IOException;
import java.io.UncheckedIOException;

@Configuration
public class R2dbcSchemaInitializerConfig {
    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(ConnectionFactory connectionFactory) {
        var initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(schemaResource()));
        return initializer;
    }

    // Read the script into memory eagerly, on the plain bean-creation thread. Otherwise
    // ResourceDatabasePopulator lazily opens the classpath resource from inside the reactive
    // population pipeline, which by then runs on the R2DBC driver's Netty event-loop thread,
    // turning the file read into a blocking call on a non-blocking thread.
    private ByteArrayResource schemaResource() {
        try (var inputStream = new ClassPathResource("r2dbc-schema.sql").getInputStream()) {
            return new ByteArrayResource(inputStream.readAllBytes());
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to read r2dbc-schema.sql", error);
        }
    }
}
