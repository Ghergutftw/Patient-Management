package com.pm.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

  Logger log = LoggerFactory.getLogger(JwtValidationGatewayFilterFactory.class);

  private final WebClient webClient;

  public JwtValidationGatewayFilterFactory(WebClient.Builder webClientBuilder,
                                           @Value("${auth.service.url}") String authServiceUrl) {
    this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
  }

  @Override
  public GatewayFilter apply(Object config) {
    return (exchange, chain) -> {
      String path = exchange.getRequest().getURI().getPath();

      // Skip JWT validation for login and register endpoints
      if (path.startsWith("/auth/login") || path.startsWith("/auth/register")) {
        return chain.filter(exchange);
      }

      String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

      log.debug("Incoming request to {} with token: {}", path, token);

      if (token == null || !token.startsWith("Bearer ")) {
        log.warn("Missing or invalid Authorization header");
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }

      log.debug("Validating token with Auth Service...");

      return webClient.get()
              .uri("/validate")
              .header(HttpHeaders.AUTHORIZATION, token)
              .retrieve()
              .toBodilessEntity()
              .doOnSuccess(response -> log.debug("Token validated successfully"))
              .doOnError(error -> log.error("Token validation failed: {}", error.getMessage()))
              .then(chain.filter(exchange));
    };
  }
}
