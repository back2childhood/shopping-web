package com.shopping.AccountService.service.impl;

import com.shopping.AccountService.dao.AccountRepository;
import com.shopping.AccountService.entity.Account;
import com.shopping.AccountService.payload.AccountRequestDto;
import com.shopping.AccountService.payload.AccountResponseDto;
import com.shopping.AccountService.service.AccountService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private AccountRepository accountRepository;
    private ModelMapper modelMapper;
    @Autowired
    public AccountServiceImpl(AccountRepository accountRepository, ModelMapper modelMapper) {
        this.accountRepository = accountRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public AccountResponseDto createAccount(AccountRequestDto dto) {
        Account account = modelMapper.map(dto, Account.class);
        account.setSeller(Boolean.TRUE.equals(dto.getIsSeller()));
        Account saved = accountRepository.save(account);
        return modelMapper.map(saved, AccountResponseDto.class);
    }

    @Override
    public AccountResponseDto updateAccount(Long id, AccountRequestDto dto) {
        Account existing = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        existing.setUsername(dto.getUsername());
        existing.setShippingAddress(dto.getShippingAddress());
        existing.setBillingAddress(dto.getBillingAddress());
        existing.setEmail(dto.getEmail());
        existing.setSeller(Boolean.TRUE.equals(dto.getIsSeller()));
        Account updated = accountRepository.save(existing);
        return modelMapper.map(updated, AccountResponseDto.class);
    }

    @Override
    public AccountResponseDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
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
        if (!accountRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        accountRepository.deleteById(id);
    }
}
