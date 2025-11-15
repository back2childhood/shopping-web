package com.shopping.AuthService.service.impl;

import com.shopping.AuthService.dao.UserRepository;
import com.shopping.AuthService.entity.User;
import com.shopping.AuthService.payload.AccountRequestDto;
import com.shopping.AuthService.payload.AuthResponse;
import com.shopping.AuthService.payload.LoginRequest;
import com.shopping.AuthService.payload.RegisterRequest;
import com.shopping.AuthService.service.AuthService;
import com.shopping.AuthService.service.JwtService;
import com.shopping.Common.exception.ResourceNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class AuthServiceImpl implements AuthService {

    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;

    private WebClient webClient;

    private ModelMapper modelMapper;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, WebClient webClient, ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.webClient = webClient;
        this.modelMapper = modelMapper;
    }

    public AuthResponse register(RegisterRequest request) {
        User user = modelMapper.map(request, User.class);

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        // Build account data to send to AccountService
        AccountRequestDto accountDTO = modelMapper.map(request, AccountRequestDto.class);

        webClient.post()
                .uri("/api/accounts")
                .bodyValue(accountDTO)
                .retrieve()
                .bodyToMono(Void.class)
                .block();


        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }
}