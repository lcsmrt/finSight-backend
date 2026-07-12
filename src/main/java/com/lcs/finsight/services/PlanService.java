package com.lcs.finsight.services;

import com.lcs.finsight.exceptions.PlanExceptions;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.PlanMembershipRepository;
import com.lcs.finsight.repositories.PlanRepository;
import com.lcs.finsight.security.PlanAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanMembershipRepository membershipRepository;
    private final PlanAuthorization planAuthorization;

    public PlanService(
            PlanRepository planRepository,
            PlanMembershipRepository membershipRepository,
            PlanAuthorization planAuthorization
    ) {
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.planAuthorization = planAuthorization;
    }

    @Transactional
    public Plan createPlan(String name, User owner) {
        return createPlanInternal(name, owner, false);
    }

    @Transactional
    public Plan provisionDefaultPlan(User owner) {
        return createPlanInternal("Meu plano", owner, true);
    }

    private Plan createPlanInternal(String name, User owner, boolean isDefault) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setCreatedBy(owner);
        plan.setDefault(isDefault);
        Plan savedPlan = planRepository.save(plan);

        PlanMembership membership = new PlanMembership();
        membership.setPlan(savedPlan);
        membership.setUser(owner);
        membership.setRole(PlanRole.OWNER);
        membershipRepository.save(membership);

        return savedPlan;
    }

    @Transactional(readOnly = true)
    public List<PlanMembership> findMembershipsForUser(User user) {
        return membershipRepository.findAllByUser(user);
    }

    @Transactional(readOnly = true)
    public PlanMembership getMembership(Long planId, User user) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new PlanExceptions.PlanNotFoundException(planId));

        return membershipRepository.findByPlanAndUser(plan, user)
                .orElseThrow(() -> new PlanExceptions.NotAMemberException(planId));
    }

    @Transactional(readOnly = true)
    public List<PlanMembership> getMembers(Long planId, User requester) {
        PlanMembership requesterMembership = getMembership(planId, requester);
        return membershipRepository.findAllByPlan(requesterMembership.getPlan());
    }

    public void requireNotLastPlan(User user) {
        if (membershipRepository.findAllByUser(user).size() <= 1) {
            throw new PlanExceptions.LastPlanException();
        }
    }

    public void requireNotLastOwner(Plan plan) {
        if (membershipRepository.countByPlanAndRole(plan, PlanRole.OWNER) <= 1) {
            throw new PlanExceptions.LastOwnerException();
        }
    }

    @Transactional
    public PlanMembership changeMemberRole(Long planId, User targetUser, PlanRole newRole, User requester) {
        PlanMembership requesterMembership = getMembership(planId, requester);
        planAuthorization.requireOwner(requesterMembership.getRole());

        Plan plan = requesterMembership.getPlan();
        PlanMembership targetMembership = membershipRepository.findByPlanAndUser(plan, targetUser)
                .orElseThrow(() -> new PlanExceptions.NotAMemberException(planId));

        if (targetMembership.getRole() == PlanRole.OWNER && newRole != PlanRole.OWNER) {
            requireNotLastOwner(plan);
        }

        targetMembership.setRole(newRole);
        return membershipRepository.save(targetMembership);
    }

    @Transactional
    public void removeMember(Long planId, User targetUser, User requester) {
        PlanMembership requesterMembership = getMembership(planId, requester);
        planAuthorization.requireOwner(requesterMembership.getRole());

        Plan plan = requesterMembership.getPlan();
        PlanMembership targetMembership = membershipRepository.findByPlanAndUser(plan, targetUser)
                .orElseThrow(() -> new PlanExceptions.NotAMemberException(planId));

        if (targetMembership.getRole() == PlanRole.OWNER) {
            requireNotLastOwner(plan);
        }

        membershipRepository.delete(targetMembership);
    }
}
