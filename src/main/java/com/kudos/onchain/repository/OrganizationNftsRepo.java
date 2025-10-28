package com.kudos.onchain.repository;

import com.kudos.onchain.model.OrganizationNfts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationNftsRepo extends JpaRepository<OrganizationNfts, Long> {

    List<OrganizationNfts> getOrganizationNftsByTeamId(String teamId);
}
