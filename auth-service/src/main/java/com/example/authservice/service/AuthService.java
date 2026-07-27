package com.example.authservice.service;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.kafka.UserEventProducer;
import com.example.authservice.model.User;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserEventProducer userEventProducer;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(new HashSet<>(Set.of("USER")))
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user id={} username={}", saved.getId(), saved.getUsername());

        userEventProducer.publishUserRegistered(saved);

        String token = jwtService.generateToken(saved.getUsername(), List.copyOf(saved.getRoles()));
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMs() / 1000)
                .username(saved.getUsername())
                .roles(saved.getRoles())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(user.getUsername(), List.copyOf(user.getRoles()));
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMs() / 1000)
                .username(user.getUsername())
                .roles(user.getRoles())
                .build();
    }
}
