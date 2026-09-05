package com.shopping.AuthService.payload;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountRequestDto {
    private String email;
    private String username;
    private String shippingAddress;
    private String billingAddress;
    private Boolean isSeller;
}
