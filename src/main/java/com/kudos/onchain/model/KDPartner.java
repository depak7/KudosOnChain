package com.kudos.onchain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kd_partner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class KDPartner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", unique = true)
    private String teamId;

    @Column(name = "team_name")
    private String teamName;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "bot_user_id")
    private String botUserId;

    @Column(name = "scope")
    private String scope;

    @Column(name = "authed_user_id")
    private String authedUserId;

    @Column(name = "enterprise_id")
    private String enterpriseId;

    @Column(name = "collection_address")
    private String collectionAddress;

}
