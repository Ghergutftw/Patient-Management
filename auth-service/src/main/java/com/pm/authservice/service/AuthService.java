package com.pm.authservice.service;

import com.pm.authservice.dto.LoginRequestDTO;
import com.pm.authservice.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import java.util.Optional;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Observed
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Optional<String> authenticate(LoginRequestDTO loginRequestDTO) {
        Optional<String> token = userService.findByEmail(loginRequestDTO.getEmail())
                .filter(u -> passwordEncoder.matches(loginRequestDTO.getPassword(),
                        u.getPassword()))
                .map(u -> jwtUtil.generateToken(u.getEmail(), u.getRole()));

        if (token.isEmpty()) {
            log.warn("Authentication failed for user: {}", loginRequestDTO.getEmail());
            return Optional.empty();
        }
        log.info("Authentication successful for user: {}", loginRequestDTO.getEmail());
        log.debug("Generated token: {}", token.get());
        return token;
    }

    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Token is null or empty");
            return false;
        }
        log.debug("Validating token: {}", token);
        try {
            jwtUtil.validateToken(token);
            log.info("Token is valid");
            return true;
        } catch (JwtException e){
            return false;
        }
    }
}