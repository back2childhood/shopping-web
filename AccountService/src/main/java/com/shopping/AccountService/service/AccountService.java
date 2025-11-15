package com.shopping.AccountService.service;

import com.shopping.AccountService.entity.Account;
import com.shopping.AccountService.payload.AccountRequestDto;
import com.shopping.AccountService.payload.AccountResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;

public interface AccountService {

    public AccountResponseDto createAccount(AccountRequestDto dto);

    public List<AccountResponseDto> getAllAccounts();

    public AccountResponseDto getAccountById(Long id);

    public AccountResponseDto updateAccount(Long id, AccountRequestDto accountDto);

    public void deleteAccount(Long id);
}
