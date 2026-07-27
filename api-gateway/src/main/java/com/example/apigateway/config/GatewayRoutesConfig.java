package com.example.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

/**
 * Explicit, readable routes rather than spring.cloud.gateway.discovery.locator auto-routing -
 * makes it obvious which path prefix maps to which Eureka-registered service.
 *
 * The "lb://SERVICE-NAME" scheme means Spring Cloud LoadBalancer resolves SERVICE-NAME against
 * the Eureka registry at request time and picks an instance - if you scale a service to N
 * replicas, requests get load-balanced across all of them automatically, no gateway config change needed.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .uri("lb://AUTH-SERVICE"))
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        // Coarse edge check only: is there an Authorization header at all?
                        // order-service still does the real JWT signature/expiry verification -
                        // this just fails fast at the edge instead of burning a network hop.
                        .filters(f -> f.filter(requireAuthorizationHeader()))
                        .uri("lb://ORDER-SERVICE"))
                .route("inventory-service", r -> r
                        .path("/api/products/**")
                        .uri("lb://INVENTORY-SERVICE"))
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .uri("lb://NOTIFICATION-SERVICE"))
                .build();
    }

    private org.springframework.cloud.gateway.filter.GatewayFilter requireAuthorizationHeader() {
        return (ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) -> {
            if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }
}
