package com.shopping.AccountService.service.impl;

import com.shopping.AccountService.dao.AccountRepository;
import com.shopping.AccountService.entity.Account;
import com.shopping.AccountService.payload.AccountRequestDto;
import com.shopping.AccountService.payload.AccountResponseDto;
import com.shopping.AccountService.service.AccountService;
import com.shopping.Common.exception.ResourceNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private AccountRepository accountRepository;
    private ModelMapper modelMapper;
    private PasswordEncoder passwordEncoder;

    @Autowired
    public AccountServiceImpl(AccountRepository accountRepository, ModelMapper modelMapper, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AccountResponseDto createAccount(AccountRequestDto dto) {
        Account account = modelMapper.map(dto, Account.class);
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        Account saved = accountRepository.save(account);
        return modelMapper.map(saved, AccountResponseDto.class);
    }

    @Override
    public AccountResponseDto updateAccount(Long id, AccountRequestDto dto) {
        Account existing = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", String.valueOf(id)));
        existing.setUsername(dto.getUsername());
        existing.setPassword(passwordEncoder.encode(dto.getPassword()));
        existing.setShippingAddress(dto.getShippingAddress());
        existing.setBillingAddress(dto.getBillingAddress());
        existing.setPaymentMethod(dto.getPaymentMethod());
        existing.setEmail(dto.getEmail());
        Account updated = accountRepository.save(existing);
        return modelMapper.map(updated, AccountResponseDto.class);
    }

    @Override
    public AccountResponseDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", String.valueOf(id)));
        return modelMapper.map(account, AccountResponseDto.class);
    }

    @Override
    public List<AccountResponseDto> getAllAccounts() {
        return accountRepository.findAll()
                .stream()
                .map(a -> modelMapper.map(a, AccountResponseDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAccount(Long id) {
        return;
    }
}
