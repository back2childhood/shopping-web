package com.shopping.AuthService.service;

import com.shopping.AuthService.entity.User;
import com.shopping.AuthService.payload.AuthResponse;
import com.shopping.AuthService.payload.LoginRequest;
import com.shopping.AuthService.payload.RegisterRequest;
import org.springframework.stereotype.Service;

public interface AuthService {

    public AuthResponse register(RegisterRequest request);

    public AuthResponse login(LoginRequest request) ;
}
