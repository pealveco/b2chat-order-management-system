package com.b2chat.ordermanagement.api.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class UserRouterRest {
    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return route(POST("/users"), handler::register)
                .andRoute(GET("/users/{id}"), handler::getById);
    }
}
