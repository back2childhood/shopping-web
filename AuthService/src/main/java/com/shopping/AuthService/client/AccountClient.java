package com.shopping.AuthService.client;

import com.shopping.AuthService.payload.AccountRequestDto;
import com.shopping.AuthService.payload.AccountResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ACCOUNT-SERVICE")
public interface AccountClient {
    @PostMapping("/api/accounts")
    AccountResponseDto createAccount(@RequestBody AccountRequestDto request);
}
