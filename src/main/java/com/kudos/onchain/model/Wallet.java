package com.kudos.onchain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "wallet_address", unique = true)
    private String walletAddress;
    @Column(name = "private_key", unique = true)
    private String privateKey;
    @Column(name = "verified", nullable = false)
    private boolean verified = false;
    @OneToOne
    @JoinColumn(name = "employee_id", referencedColumnName = "id")
    private PTEmployee member;
}
