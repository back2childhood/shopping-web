package com.shopping;

import com.shopping.AccountService.dao.AccountRepository;
import com.shopping.AccountService.entity.Account;
import com.shopping.AccountService.payload.AccountResponseDto;
import com.shopping.AccountService.service.impl.AccountServiceImpl;
import com.shopping.Common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AccountServiceImpl accountService;

    @Test
    void testGetAccountByEmail_Success() {
        Account account = new Account();
        account.setEmail("test@example.com");

        AccountResponseDto dto = new AccountResponseDto();
        dto.setEmail("test@example.com");

        accountRepository.save(account);

        when(accountRepository.getAccountByEmail("test@example.com"))
                .thenReturn(account);
        when(modelMapper.map(account, AccountResponseDto.class))
                .thenReturn(dto);

//        AccountResponseDto result = accountService.getAccountByEmail("test@example.com");
//
//        assertNotNull(result);
//        assertEquals("test@example.com", result.getEmail());
//        verify(accountRepository, times(1)).getAccountByEmail("test@example.com");
    }

    @Test
    void testGetAccountByEmail_NotFound() {
        when(accountRepository.getAccountByEmail("notfound@example.com"))
                .thenReturn(null);

//        assertThrows(ResourceNotFoundException.class, () -> {
//            accountService.getAccountByEmail("notfound@example.com");
//        });
    }
}