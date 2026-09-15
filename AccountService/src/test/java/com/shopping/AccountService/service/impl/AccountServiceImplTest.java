package com.shopping.AccountService.service.impl;

import com.shopping.AccountService.dao.AccountRepository;
import com.shopping.AccountService.entity.Account;
import com.shopping.AccountService.payload.AccountRequestDto;
import com.shopping.AccountService.payload.AccountResponseDto;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceImplTest {

    @Test
    void createAccountReturnsExistingGlobalIdentityForRegistrationRetry() {
        Account existing = new Account();
        existing.setId(42L);
        existing.setEmail("seller@example.com");
        existing.setUsername("Seller");
        existing.setSeller(true);
        AccountRepository repository = (AccountRepository) Proxy.newProxyInstance(
                AccountRepository.class.getClassLoader(),
                new Class<?>[]{AccountRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByEmail")) {
                        assertThat(args[0]).isEqualTo("seller@example.com");
                        return Optional.of(existing);
                    }
                    if (method.getName().equals("save")) {
                        throw new AssertionError("An idempotent retry must not create another account");
                    }
                    return null;
                });

        AccountServiceImpl service = new AccountServiceImpl(repository, new ModelMapper());
        AccountRequestDto request = AccountRequestDto.builder()
                .email("  SELLER@example.com ")
                .username("Seller")
                .isSeller(true)
                .build();

        AccountResponseDto response = service.createAccount(request);

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getEmail()).isEqualTo("seller@example.com");
        assertThat(response.getIsSeller()).isTrue();
    }
}
