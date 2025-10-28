package com.kudos.onchain.repository;

import com.kudos.onchain.model.KDPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KDPartnerRepo extends JpaRepository<KDPartner, Long> {
   KDPartner findByTeamId(String teamId);
}
