package com.kudos.onchain.repository;

import com.kudos.onchain.model.KDPartner;
import com.kudos.onchain.model.PTEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PTEmployeeRepo extends JpaRepository<PTEmployee, Long> {
    PTEmployee getPTEmployeeByUserId(String userId);

    List<PTEmployee> findByKdPartner(KDPartner kdPartner);
}
