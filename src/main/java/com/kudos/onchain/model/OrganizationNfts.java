package com.kudos.onchain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "organization_nfts")
@Data
public class OrganizationNfts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mint_address", unique = true, nullable = false)
    private String mintAddress;

    @Column(name = "collection_address")
    private String collectionAddress;

    @Column(name = "team_id", nullable = false)
    private String teamId;  // could also be Long if you have a Team entity

    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "minted_by_employee_id")
    private String mintedByEmployeeId;


    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
