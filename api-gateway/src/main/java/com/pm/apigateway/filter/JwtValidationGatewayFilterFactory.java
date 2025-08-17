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
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;

@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

  private static final Logger log = LoggerFactory.getLogger(JwtValidationGatewayFilterFactory.class);
  private final WebClient webClient;

  public JwtValidationGatewayFilterFactory(WebClient.Builder webClientBuilder,
                                           @Value("${auth.service.url}") String authServiceUrl) {
    this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
  }

  @Override
  public GatewayFilter apply(Object config) {
    return (exchange, chain) -> {
      String path = exchange.getRequest().getURI().getPath();

      if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
        return chain.filter(exchange);
      }

      return validateToken(exchange, chain);
    };
  }

  private Mono<Void> validateToken(ServerWebExchange exchange, GatewayFilterChain chain) {
    String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    if (token == null || !token.startsWith("Bearer ")) {
      log.warn("Missing or invalid Authorization header for request to {}", exchange.getRequest().getURI().getPath());
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    log.debug("Validating token with Auth Service...");
    return webClient.get()
            .uri("/validate")
            .header(HttpHeaders.AUTHORIZATION, token)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(response -> log.debug("Token validation successful."))
            .doOnError(error -> log.error("Token validation failed: {}", error.getMessage()))
            .then(chain.filter(exchange));
  }
}