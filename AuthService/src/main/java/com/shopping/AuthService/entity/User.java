package com.shopping.AuthService.entity;


import jakarta.persistence.*;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    private String username;
    private String password;

    private String shippingAddress;
    private String billingAddress;
    @Description("1 - credit card, 2 - debit card, 3 - paypal")
    private Integer paymentMethod;

    private Boolean isSeller;
}