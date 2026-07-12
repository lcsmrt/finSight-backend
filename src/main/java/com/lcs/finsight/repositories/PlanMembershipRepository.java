package com.lcs.finsight.repositories;

import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanMembershipRepository extends JpaRepository<PlanMembership, Long> {

    @Query("select m from PlanMembership m where m.user = :user and m.plan.deletedAt is null")
    List<PlanMembership> findAllByUser(@Param("user") User user);

    Optional<PlanMembership> findByPlanAndUser(Plan plan, User user);

    boolean existsByPlanAndUser(Plan plan, User user);

    List<PlanMembership> findAllByPlan(Plan plan);

    long countByPlanAndRole(Plan plan, PlanRole role);
}
