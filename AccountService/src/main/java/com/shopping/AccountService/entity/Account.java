package com.shopping.AccountService.entity;

import jakarta.persistence.*;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

//    @Column(nullable = false)

    private String shippingAddress;
    private String billingAddress;
    @Column(nullable = false)
    @Description("1 - credit card, 2 - debit card, 3 - paypal")
    private Integer paymentMethod;

    @Column(nullable = true)
    private boolean isSeller;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
