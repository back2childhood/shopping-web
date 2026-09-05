package com.shopping.AccountService.payload;

import lombok.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountRequestDto {
    @Email @NotBlank
    private String email;
    @NotBlank
    private String username;
    private String shippingAddress;
    private String billingAddress;
    private Boolean isSeller;
}
