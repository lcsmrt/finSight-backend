package com.lcs.finsight.support;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.TransactionParticipant;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.repositories.PlanMembershipRepository;
import com.lcs.finsight.repositories.PlanRepository;
import com.lcs.finsight.repositories.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@TestComponent
public class Fixtures {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanMembershipRepository planMembershipRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    public User aUser() {
        return aUser("user-" + UUID.randomUUID() + "@test.com");
    }

    public User aUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword(ENCODER.encode("password"));
        return userRepository.save(user);
    }

    /** Mirrors PlanService.createPlanInternal: creates the plan and auto-adds the owner as an OWNER member. */
    public Plan aPlan(User owner) {
        Plan plan = new Plan();
        plan.setName("Test Plan " + UUID.randomUUID());
        plan.setCreatedBy(owner);
        plan.setDefault(false);
        Plan savedPlan = planRepository.save(plan);
        addMember(savedPlan, owner, PlanRole.OWNER);
        return savedPlan;
    }

    public PlanMembership addMember(Plan plan, User user, PlanRole role) {
        PlanMembership membership = new PlanMembership();
        membership.setPlan(plan);
        membership.setUser(user);
        membership.setRole(role);
        return planMembershipRepository.save(membership);
    }

    public FinancialTransaction aTransaction(Plan plan, User createdBy, BigDecimal amount, FinancialTransactionType type) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setPlan(plan);
        transaction.setCreatedBy(createdBy);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setDescription("Test transaction");
        transaction.setStartDate(LocalDate.now());

        TransactionParticipant participant = new TransactionParticipant();
        participant.setTransaction(transaction);
        participant.setMember(createdBy);
        participant.setShareAmount(amount);
        transaction.getParticipants().add(participant);

        return financialTransactionRepository.save(transaction);
    }
}
