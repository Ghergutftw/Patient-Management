package com.pm.authservice.service;

import com.pm.authservice.model.User;
import com.pm.authservice.repository.UserRepository;
import java.util.Optional;

import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

@Service
@Observed
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}