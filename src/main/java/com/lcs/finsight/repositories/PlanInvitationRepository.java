package com.lcs.finsight.repositories;

import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanInvitationRepository extends JpaRepository<PlanInvitation, Long> {

    Optional<PlanInvitation> findByToken(String token);

    List<PlanInvitation> findAllByPlan(Plan plan);
}
