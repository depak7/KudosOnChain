package com.kudos.onchain.repository;

import com.kudos.onchain.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepo extends JpaRepository<Wallet, Long> {
    Wallet findWalletByMember_UserId(String userId);
}
