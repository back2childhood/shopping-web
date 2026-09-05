package com.shopping.AuthService.service.impl;

import com.shopping.AuthService.dao.UserRepository;
import com.shopping.AuthService.entity.User;
import com.shopping.AuthService.payload.AccountRequestDto;
import com.shopping.AuthService.payload.AuthResponse;
import com.shopping.AuthService.payload.LoginRequest;
import com.shopping.AuthService.payload.RegisterRequest;
import com.shopping.AuthService.service.AuthService;
import com.shopping.AuthService.service.JwtService;
import com.shopping.AuthService.client.AccountClient;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {

    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;

    private AccountClient accountClient;

    private ModelMapper modelMapper;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AccountClient accountClient, ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accountClient = accountClient;
        this.modelMapper = modelMapper;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setIsSeller(Boolean.TRUE.equals(request.getIsSeller()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        // Build account data to send to AccountService
        AccountRequestDto accountDTO = modelMapper.map(request, AccountRequestDto.class);

        accountClient.createAccount(accountDTO);


        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getIsSeller());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getIsSeller());
    }
}
