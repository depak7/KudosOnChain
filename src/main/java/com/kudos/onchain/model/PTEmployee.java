package com.kudos.onchain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pt_employee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PTEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private String userId;
    @Column(name = "user_name")
    private String userName;
    @Column(name = "email")
    private String email;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kd_partner_id",referencedColumnName = "id")
    private KDPartner kdPartner;
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Wallet wallet;

}
