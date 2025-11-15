package com.shopping.AccountService.dao;

import com.shopping.AccountService.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Account getAccountById(Long id);

    Account getAccountByEmail(String email);
//    Account 
}
