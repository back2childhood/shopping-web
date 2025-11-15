package com.shopping.AuthService.payload;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String username;
    private String password;

    private String shippingAddress;
    private String billingAddress;
    private Integer paymentMethod;

    private Boolean isSeller;
}