package com.shopping.AuthService.payload;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class RegisterRequest {
    @Email @NotBlank
    private String email;
    @NotBlank
    private String username;
    @NotBlank @Size(min = 8)
    private String password;

    private String shippingAddress;
    private String billingAddress;
    private Boolean isSeller;
}
