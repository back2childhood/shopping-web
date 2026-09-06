package com.shopping.AuthService.payload;

import lombok.Data;

@Data
public class AccountResponseDto {
    private Long id;
    private String email;
    private String username;
    private Boolean isSeller;
}
