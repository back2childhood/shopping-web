package com.shopping.AccountService.payload;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountRequestDto {
    private String email;
    private String username;
    private String password;
    private String shippingAddress;
    private String billingAddress;
    private Integer paymentMethod;
    private Boolean isSeller;
}