package com.b2chat.ordermanagement.api.order;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.PUT;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class OrderRouterRest {
    @Bean
    public RouterFunction<ServerResponse> orderRoutes(OrderHandler handler) {
        return route(POST("/orders"), handler::place)
                .andRoute(GET("/orders/{id}"), handler::getById)
                .andRoute(PUT("/orders/{id}/status"), handler::updateStatus);
    }
}
