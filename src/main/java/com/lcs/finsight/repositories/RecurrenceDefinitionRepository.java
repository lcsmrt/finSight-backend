package com.lcs.finsight.repositories;

import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecurrenceDefinitionRepository extends JpaRepository<RecurrenceDefinition, Long> {

    Optional<RecurrenceDefinition> findByPlanAndSeriesId(Plan plan, String seriesId);
}
