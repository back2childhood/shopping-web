package com.shopping.AccountService.payload;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponseDto {
    private Long id;
    private String email;
    private String username;
    private String shippingAddress;
    private String billingAddress;
    private Integer paymentMethod;
    private Boolean isSeller;
    private LocalDateTime createdAt;
}