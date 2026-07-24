package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.dtos.request.ItemDto;
import com.lcs.finsight.dtos.request.ParticipantDto;
import com.lcs.finsight.dtos.request.SeriesEditRequestDto;
import com.lcs.finsight.dtos.response.FinancialTransactionSeriesResponseDto;
import com.lcs.finsight.dtos.response.RecurrenceDefinitionResponseDto;
import com.lcs.finsight.exceptions.FinancialTransactionExceptions;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceDefinitionParticipant;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SeriesEditScope;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.TransactionItem;
import com.lcs.finsight.models.TransactionParticipant;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.repositories.PlanMembershipRepository;
import com.lcs.finsight.repositories.RecurrenceDefinitionRepository;
import com.lcs.finsight.repositories.UserRepository;
import com.lcs.finsight.security.PlanAuthorization;
import com.lcs.finsight.security.PlanContext;
import com.lcs.finsight.specifications.FinancialTransactionSpecification;
import com.lcs.finsight.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialTransactionCategoryService financialTransactionCategoryService;
    private final DateUtils dateUtils;
    private final RecurringTransactionGenerator recurringTransactionGenerator;
    private final PlanAuthorization planAuthorization;
    private final SplitResolver splitResolver;
    private final PlanMembershipRepository planMembershipRepository;
    private final UserRepository userRepository;
    private final RecurrenceDefinitionRepository recurrenceDefinitionRepository;
    private final SeriesRegenerator seriesRegenerator;

    public FinancialTransactionService(
            FinancialTransactionRepository financialTransactionRepository,
            FinancialTransactionCategoryService financialTransactionCategoryService,
            DateUtils dateUtils,
            RecurringTransactionGenerator recurringTransactionGenerator,
            PlanAuthorization planAuthorization,
            SplitResolver splitResolver,
            PlanMembershipRepository planMembershipRepository,
            UserRepository userRepository,
            RecurrenceDefinitionRepository recurrenceDefinitionRepository,
            SeriesRegenerator seriesRegenerator
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionCategoryService = financialTransactionCategoryService;
        this.dateUtils = dateUtils;
        this.recurringTransactionGenerator = recurringTransactionGenerator;
        this.planAuthorization = planAuthorization;
        this.splitResolver = splitResolver;
        this.planMembershipRepository = planMembershipRepository;
        this.userRepository = userRepository;
        this.recurrenceDefinitionRepository = recurrenceDefinitionRepository;
        this.seriesRegenerator = seriesRegenerator;
    }

    @Transactional(readOnly = true)
    public FinancialTransaction findById(Long id, PlanContext ctx) {
        return financialTransactionRepository.findByIdAndPlan(id, ctx.getPlan())
                .orElseThrow(() -> new FinancialTransactionExceptions.FinancialTransactionNotFoundException(id));
    }

    private static final String SORT_CATEGORY = "category";
    private static final String SORT_ATTRIBUTED_TO = "attributedTo";
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("startDate", "endDate", "amount", "description", SORT_CATEGORY, SORT_ATTRIBUTED_TO);

    @Transactional(readOnly = true)
    public List<FinancialTransaction> findAllByPlan(PlanContext ctx) {
        return financialTransactionRepository.findAllByPlan(ctx.getPlan());
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransaction> findAllByPlanPaged(FinancialTransactionFilterDto filter, PlanContext ctx) {
        String sortBy = filter.getSortBy();
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy);
        }
        Sort.Direction direction = Sort.Direction.fromString(filter.getSortDirection());

        List<Specification<FinancialTransaction>> specs = new ArrayList<>(List.of(
                FinancialTransactionSpecification.belongsToPlan(ctx.getPlan()),
                FinancialTransactionSpecification.typeEquals(filter.getType()),
                FinancialTransactionSpecification.categoryEquals(filter.getCategoryId()),
                FinancialTransactionSpecification.hasParticipant(filter.getMemberId()),
                FinancialTransactionSpecification.matchesSearchTerm(filter.getDescription()),
                FinancialTransactionSpecification.startDateFrom(filter.getStartDateFrom()),
                FinancialTransactionSpecification.startDateTo(filter.getStartDateTo()),
                FinancialTransactionSpecification.amountMin(filter.getAmountMin()),
                FinancialTransactionSpecification.amountMax(filter.getAmountMax())));

        // category / attributed-to are aggregate/association orderings the Specification owns; for those
        // the Pageable is left unsorted so the spec's ORDER BY is the only one applied. Simple property
        // sorts (startDate/endDate/amount/description) stay on the Pageable as before.
        PageRequest pageable;
        if (SORT_CATEGORY.equals(sortBy)) {
            specs.add(FinancialTransactionSpecification.orderByCategoryName(direction));
            pageable = PageRequest.of(filter.getPage(), filter.getSize());
        } else if (SORT_ATTRIBUTED_TO.equals(sortBy)) {
            specs.add(FinancialTransactionSpecification.orderByLargestShareParticipant(direction));
            pageable = PageRequest.of(filter.getPage(), filter.getSize());
        } else {
            pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(direction, sortBy));
        }

        return financialTransactionRepository.findAll(Specification.allOf(specs), pageable);
    }

    @Transactional
    public FinancialTransaction create(FinancialTransactionRequestDto dto, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());
        dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());

        FinancialTransaction financialTransaction = new FinancialTransaction();
        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new FinancialTransactionExceptions.CategoryTypeMismatchException();
        }

        financialTransaction.setPlan(ctx.getPlan());
        financialTransaction.setCreatedBy(ctx.getUser());
        financialTransaction.setCategory(category);
        financialTransaction.setType(dto.getType());
        financialTransaction.setAmount(dto.getAmount());
        financialTransaction.setDescription(dto.getDescription());
        financialTransaction.setParcelsNumber(dto.getParcelsNumber());
        financialTransaction.setStartDate(dto.getStartDate());
        financialTransaction.setEndDate(dto.getEndDate());

        applyParticipants(financialTransaction, dto.getParticipants(), dto.getSplitMode(), dto.getAmount(), ctx);
        applyItems(financialTransaction, dto.getItems(), ctx);

        return financialTransactionRepository.save(financialTransaction);
    }

    @Transactional
    public FinancialTransaction update(Long id, FinancialTransactionRequestDto dto, PlanContext ctx) {
        dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());

        FinancialTransaction existingTransaction = findById(id, ctx);
        planAuthorization.requireCanModifyTransaction(ctx.getRole(), existingTransaction.getCreatedBy(), ctx.getUser());

        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new FinancialTransactionExceptions.CategoryTypeMismatchException();
        }

        existingTransaction.setCategory(category);
        existingTransaction.setType(dto.getType());
        existingTransaction.setAmount(dto.getAmount());
        existingTransaction.setDescription(dto.getDescription());
        existingTransaction.setParcelsNumber(dto.getParcelsNumber());
        existingTransaction.setStartDate(dto.getStartDate());
        existingTransaction.setEndDate(dto.getEndDate());

        applyParticipants(existingTransaction, dto.getParticipants(), dto.getSplitMode(), dto.getAmount(), ctx);
        applyItems(existingTransaction, dto.getItems(), ctx);

        return financialTransactionRepository.save(existingTransaction);
    }

    private record ResolvedParticipants(SplitMode splitMode, List<ResolvedParticipant> shares) {}

    private ResolvedParticipants resolveParticipants(
            List<ParticipantDto> participantInputs, SplitMode requestedSplitMode, BigDecimal amount, PlanContext ctx) {
        if (participantInputs == null || participantInputs.isEmpty()) {
            return new ResolvedParticipants(SplitMode.EQUAL, List.of(new ResolvedParticipant(ctx.getUser(), amount)));
        }

        Set<Long> seenMemberIds = new HashSet<>();
        Map<Long, User> membersById = new LinkedHashMap<>();
        List<SplitResolver.ParticipantInput> resolverInputs = new ArrayList<>();
        for (ParticipantDto input : participantInputs) {
            if (!seenMemberIds.add(input.getMemberId())) {
                throw new IllegalArgumentException("Duplicate participant in transaction.");
            }
            User member = userRepository.findById(input.getMemberId())
                    .filter(u -> planMembershipRepository.existsByPlanAndUser(ctx.getPlan(), u))
                    .orElseThrow(() -> new IllegalArgumentException("Participant is not a member of the plan."));
            membersById.put(member.getId(), member);
            resolverInputs.add(new SplitResolver.ParticipantInput(member.getId(), input.getShareAmount()));
        }

        SplitMode splitMode = requestedSplitMode != null ? requestedSplitMode : SplitMode.EQUAL;
        List<SplitResolver.ResolvedShare> resolvedShares = splitResolver.resolve(amount, splitMode, resolverInputs);

        List<ResolvedParticipant> shares = resolvedShares.stream()
                .map(share -> new ResolvedParticipant(membersById.get(share.memberId()), share.shareAmount()))
                .toList();

        return new ResolvedParticipants(splitMode, shares);
    }

    private void requireAttributionAuthorizedIfNeeded(ResolvedParticipants resolved, PlanContext ctx) {
        boolean isSelfOnly = resolved.shares().size() == 1
                && resolved.shares().get(0).member().getId().equals(ctx.getUser().getId());
        if (!isSelfOnly) {
            planAuthorization.requireCanAttributeToOthers(ctx.getRole());
        }
    }

    private void applyParticipants(
            FinancialTransaction transaction, List<ParticipantDto> participantInputs,
            SplitMode requestedSplitMode, BigDecimal amount, PlanContext ctx) {
        ResolvedParticipants resolved = resolveParticipants(participantInputs, requestedSplitMode, amount, ctx);
        requireAttributionAuthorizedIfNeeded(resolved, ctx);

        transaction.setSplitMode(resolved.splitMode());

        Map<Long, TransactionParticipant> existingByMemberId = new LinkedHashMap<>();
        for (TransactionParticipant participant : transaction.getParticipants()) {
            existingByMemberId.put(participant.getMember().getId(), participant);
        }

        Set<Long> keptMemberIds = new HashSet<>();
        for (ResolvedParticipant share : resolved.shares()) {
            keptMemberIds.add(share.member().getId());
            TransactionParticipant existing = existingByMemberId.get(share.member().getId());
            if (existing != null) {
                existing.setShareAmount(share.shareAmount());
            } else {
                TransactionParticipant participant = new TransactionParticipant();
                participant.setTransaction(transaction);
                participant.setMember(share.member());
                participant.setShareAmount(share.shareAmount());
                transaction.getParticipants().add(participant);
            }
        }

        transaction.getParticipants().removeIf(
                participant -> !keptMemberIds.contains(participant.getMember().getId()));
    }

    private void applyItems(FinancialTransaction transaction, List<ItemDto> itemInputs, PlanContext ctx) {
        if (itemInputs == null || itemInputs.isEmpty()) {
            transaction.getItems().clear();
            return;
        }

        List<TransactionItem> resolvedItems = new ArrayList<>();
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ItemDto input : itemInputs) {
            if (input.getDescription() == null || input.getDescription().isBlank()) {
                throw new IllegalArgumentException("Item description cannot be blank.");
            }
            if (input.getAmount() == null || input.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Item amount must be positive.");
            }

            FinancialTransactionCategory itemCategory = input.getCategoryId() != null
                    ? financialTransactionCategoryService.findById(input.getCategoryId(), ctx)
                    : null;

            if (itemCategory != null && itemCategory.getType() != transaction.getType()) {
                throw new FinancialTransactionExceptions.ItemCategoryTypeMismatchException();
            }

            TransactionItem item = new TransactionItem();
            item.setTransaction(transaction);
            item.setDescription(input.getDescription());
            item.setAmount(input.getAmount());
            item.setCategory(itemCategory);
            item.setQuantity(input.getQuantity() != null ? input.getQuantity() : 1);

            resolvedItems.add(item);
            itemsTotal = itemsTotal.add(input.getAmount());
        }

        if (itemsTotal.compareTo(transaction.getAmount()) > 0) {
            throw new FinancialTransactionExceptions.ItemsTotalExceedsAmountException();
        }

        transaction.getItems().clear();
        transaction.getItems().addAll(resolvedItems);
    }

    @Transactional
    public void delete(Long id, PlanContext ctx) {
        FinancialTransaction transaction = findById(id, ctx);
        planAuthorization.requireCanModifyTransaction(ctx.getRole(), transaction.getCreatedBy(), ctx.getUser());
        financialTransactionRepository.delete(transaction);
    }

    @Transactional
    public List<FinancialTransaction> createSeries(FinancialTransactionSeriesRequestDto dto, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());

        if (dto.getMode() == RecurrenceMode.INSTALLMENT) {
            if (dto.getParcelsNumber() == null) {
                throw new IllegalArgumentException("Parcels number is required for installment series.");
            }
            if (dto.getCurrentParcel() != null
                    && (dto.getCurrentParcel() < 1 || dto.getCurrentParcel() > dto.getParcelsNumber())) {
                throw new IllegalArgumentException("Current parcel must be between 1 and the total number of parcels.");
            }
        } else if (dto.getMode() == RecurrenceMode.RECURRING) {
            if (dto.getInterval() == null) {
                throw new IllegalArgumentException("Interval is required for recurring series.");
            }
            // endDate is optional: null means open-ended (materialized on a rolling horizon, see
            // RecurringTransactionGenerator.OPEN_ENDED_HORIZON_MONTHS / generatedThrough below).
            dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());
        }

        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new FinancialTransactionExceptions.CategoryTypeMismatchException();
        }

        ResolvedParticipants resolvedParticipants = resolveParticipants(dto.getParticipants(), dto.getSplitMode(), dto.getAmount(), ctx);
        requireAttributionAuthorizedIfNeeded(resolvedParticipants, ctx);

        String seriesId = UUID.randomUUID().toString();
        List<FinancialTransaction> occurrences = recurringTransactionGenerator.generate(
                dto, ctx.getPlan(), ctx.getUser(), category, seriesId,
                resolvedParticipants.splitMode(), resolvedParticipants.shares());

        RecurrenceDefinition definition = new RecurrenceDefinition();
        definition.setPlan(ctx.getPlan());
        definition.setCreatedBy(ctx.getUser());
        definition.setCategory(category);
        definition.setSeriesId(seriesId);
        definition.setType(dto.getType());
        definition.setAmount(dto.getAmount());
        definition.setDescription(dto.getDescription());
        definition.setMode(dto.getMode());
        definition.setRecurrenceInterval(dto.getInterval());
        definition.setParcelsNumber(dto.getParcelsNumber());
        definition.setFirstParcel(dto.getMode() == RecurrenceMode.INSTALLMENT
                ? (dto.getCurrentParcel() != null ? dto.getCurrentParcel() : 1)
                : null);
        definition.setStartDate(dto.getStartDate());
        definition.setEndDate(dto.getEndDate());
        definition.setSplitMode(resolvedParticipants.splitMode());
        if (dto.getMode() == RecurrenceMode.RECURRING && !occurrences.isEmpty()) {
            definition.setGeneratedThrough(occurrences.get(occurrences.size() - 1).getStartDate());
        }

        for (ResolvedParticipant share : resolvedParticipants.shares()) {
            RecurrenceDefinitionParticipant participant = new RecurrenceDefinitionParticipant();
            participant.setDefinition(definition);
            participant.setMember(share.member());
            participant.setShareAmount(share.shareAmount());
            definition.getParticipants().add(participant);
        }

        recurrenceDefinitionRepository.save(definition);

        for (FinancialTransaction occurrence : occurrences) {
            occurrence.setRecurrenceDefinition(definition);
        }

        return financialTransactionRepository.saveAll(occurrences);
    }

    @Transactional
    public void deleteSeries(String seriesId, SeriesEditScope scope, Long pivotOccurrenceId, PlanContext ctx) {
        List<FinancialTransaction> occurrences = financialTransactionRepository.findAllByPlanAndSeriesId(ctx.getPlan(), seriesId);

        if (occurrences.isEmpty()) {
            throw new FinancialTransactionExceptions.FinancialTransactionSeriesNotFoundException(seriesId);
        }

        FinancialTransaction pivot = null;
        if (scope != SeriesEditScope.ALL) {
            pivot = occurrences.stream()
                    .filter(occurrence -> occurrence.getId().equals(pivotOccurrenceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Pivot occurrence not found in this series."));
        }

        List<FinancialTransaction> inScope;
        switch (scope) {
            case THIS_ONE -> inScope = List.of(pivot);
            case THIS_AND_FOLLOWING -> {
                LocalDate pivotStartDate = pivot.getStartDate();
                inScope = occurrences.stream()
                        .filter(occurrence -> !occurrence.getStartDate().isBefore(pivotStartDate))
                        .toList();
            }
            case ALL -> inScope = occurrences;
            default -> throw new IllegalStateException("Unexpected series edit scope: " + scope);
        }

        for (FinancialTransaction occurrence : inScope) {
            planAuthorization.requireCanModifyTransaction(ctx.getRole(), occurrence.getCreatedBy(), ctx.getUser());
        }

        financialTransactionRepository.deleteAll(inScope);

        Set<Long> deletedIds = new HashSet<>();
        for (FinancialTransaction occurrence : inScope) {
            deletedIds.add(occurrence.getId());
        }
        List<FinancialTransaction> remaining = occurrences.stream()
                .filter(occurrence -> !deletedIds.contains(occurrence.getId()))
                .toList();

        RecurrenceDefinition definition = recurrenceDefinitionRepository
                .findByPlanAndSeriesId(ctx.getPlan(), seriesId)
                .orElse(null);

        if (remaining.isEmpty()) {
            if (definition != null) {
                recurrenceDefinitionRepository.delete(definition);
            }
            return;
        }

        // THIS_AND_FOLLOWING left earlier occurrences behind: shrink the definition's range so a
        // later regeneration won't recreate the deleted tail. THIS_ONE leaves a gap the exception-less
        // model can't represent, so the definition is untouched (matching edit THIS_ONE).
        if (scope == SeriesEditScope.THIS_AND_FOLLOWING && definition != null) {
            if (definition.getMode() == RecurrenceMode.INSTALLMENT) {
                int firstParcel = definition.getFirstParcel() != null ? definition.getFirstParcel() : 1;
                definition.setParcelsNumber(firstParcel + remaining.size() - 1);
            } else {
                LocalDate newEndDate = remaining.stream()
                        .map(FinancialTransaction::getStartDate)
                        .max(LocalDate::compareTo)
                        .orElse(definition.getEndDate());
                definition.setEndDate(newEndDate);
            }
            recurrenceDefinitionRepository.save(definition);
        }
    }

    @Transactional(readOnly = true)
    public RecurrenceDefinitionResponseDto getSeriesDefinition(String seriesId, PlanContext ctx) {
        RecurrenceDefinition definition = recurrenceDefinitionRepository.findByPlanAndSeriesId(ctx.getPlan(), seriesId)
                .orElseThrow(() -> new FinancialTransactionExceptions.FinancialTransactionSeriesNotFoundException(seriesId));
        return new RecurrenceDefinitionResponseDto(definition);
    }

    private static final Pattern INSTALLMENT_SUFFIX_PATTERN = Pattern.compile("\\(\\d+/\\d+\\)$");

    private String extractInstallmentSuffix(String description) {
        if (description == null) {
            return null;
        }
        Matcher matcher = INSTALLMENT_SUFFIX_PATTERN.matcher(description);
        return matcher.find() ? matcher.group() : null;
    }

    @Transactional
    public FinancialTransactionSeriesResponseDto editSeries(String seriesId, SeriesEditRequestDto dto, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());

        RecurrenceDefinition definition = recurrenceDefinitionRepository.findByPlanAndSeriesId(ctx.getPlan(), seriesId)
                .orElseThrow(() -> new FinancialTransactionExceptions.FinancialTransactionSeriesNotFoundException(seriesId));

        List<FinancialTransaction> occurrences = financialTransactionRepository.findAllByPlanAndSeriesId(ctx.getPlan(), seriesId);

        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new FinancialTransactionExceptions.CategoryTypeMismatchException();
        }

        if (dto.getParcelsNumber() != null
                && !Objects.equals(dto.getParcelsNumber(), definition.getParcelsNumber())
                && dto.getScope() != SeriesEditScope.ALL) {
            throw new IllegalArgumentException(
                    "Changing the number of parcels is only allowed when editing the whole series.");
        }

        ResolvedParticipants resolved = resolveParticipants(dto.getParticipants(), dto.getSplitMode(), dto.getAmount(), ctx);
        requireAttributionAuthorizedIfNeeded(resolved, ctx);

        FinancialTransaction pivot = null;
        if (dto.getScope() != SeriesEditScope.ALL) {
            Long pivotOccurrenceId = dto.getPivotOccurrenceId();
            pivot = occurrences.stream()
                    .filter(occurrence -> occurrence.getId().equals(pivotOccurrenceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Pivot occurrence not found in this series."));
        }

        List<FinancialTransaction> inScope;
        switch (dto.getScope()) {
            case THIS_ONE -> inScope = List.of(pivot);
            case THIS_AND_FOLLOWING -> {
                LocalDate pivotStartDate = pivot.getStartDate();
                inScope = occurrences.stream()
                        .filter(occurrence -> !occurrence.getStartDate().isBefore(pivotStartDate))
                        .toList();
            }
            case ALL -> inScope = occurrences;
            default -> throw new IllegalStateException("Unexpected series edit scope: " + dto.getScope());
        }

        for (FinancialTransaction occurrence : inScope) {
            planAuthorization.requireCanModifyTransaction(ctx.getRole(), occurrence.getCreatedBy(), ctx.getUser());
        }

        if (dto.getScope() == SeriesEditScope.THIS_ONE) {
            String suffix = extractInstallmentSuffix(pivot.getDescription());
            pivot.setDescription(suffix != null ? dto.getDescription() + " " + suffix : dto.getDescription());
            pivot.setAmount(dto.getAmount());
            pivot.setCategory(category);
            pivot.setSplitMode(resolved.splitMode());

            reconcileParticipants(pivot, resolved.shares());

            financialTransactionRepository.save(pivot);
            return new FinancialTransactionSeriesResponseDto(List.of(pivot));
        }

        definition.setAmount(dto.getAmount());
        definition.setDescription(dto.getDescription());
        definition.setCategory(category);
        definition.setSplitMode(resolved.splitMode());
        definition.setParcelsNumber(dto.getParcelsNumber());
        // Full-replace (matches the transaction PUT contract): a null endDate here means "make this
        // RECURRING series open-ended," not "leave the bound unchanged" (P3, RMV2-09).
        definition.setEndDate(dto.getEndDate());

        reconcileDefinitionParticipants(definition, resolved.shares());

        LocalDate pivotDate = dto.getScope() == SeriesEditScope.ALL ? null : pivot.getStartDate();

        SeriesRegenerator.SeriesEditResult result = seriesRegenerator.reconcile(
                definition, occurrences, resolved.shares(), dto.getScope(), pivotDate);

        financialTransactionRepository.saveAll(result.toUpdate());
        financialTransactionRepository.saveAll(result.toCreate());
        financialTransactionRepository.deleteAll(result.toDelete());

        List<FinancialTransaction> combined = new ArrayList<>(result.toUpdate());
        combined.addAll(result.toCreate());

        // P3: keep generatedThrough in lockstep with the (possibly just toggled) endDate. Bounding
        // (endDate now set) caps the watermark at the new endDate, which also stops the rolling
        // top-up (its due-query only selects endDate IS NULL). Re-opening (endDate now null) just
        // regenerated the series out to the rolling horizon via SeriesRegenerator's own open-ended
        // fallback, so the watermark advances to the last occurrence that reconcile actually produced.
        if (definition.getMode() == RecurrenceMode.RECURRING) {
            LocalDate generatedThrough = definition.getEndDate() != null
                    ? definition.getEndDate()
                    : combined.stream()
                            .map(FinancialTransaction::getStartDate)
                            .max(LocalDate::compareTo)
                            .orElse(definition.getGeneratedThrough());
            definition.setGeneratedThrough(generatedThrough);
        }

        recurrenceDefinitionRepository.save(definition);

        return new FinancialTransactionSeriesResponseDto(combined);
    }

    private void reconcileParticipants(FinancialTransaction transaction, List<ResolvedParticipant> shares) {
        Map<Long, TransactionParticipant> existingByMemberId = new LinkedHashMap<>();
        for (TransactionParticipant participant : transaction.getParticipants()) {
            existingByMemberId.put(participant.getMember().getId(), participant);
        }

        Set<Long> keptMemberIds = new HashSet<>();
        for (ResolvedParticipant share : shares) {
            keptMemberIds.add(share.member().getId());
            TransactionParticipant existing = existingByMemberId.get(share.member().getId());
            if (existing != null) {
                existing.setShareAmount(share.shareAmount());
            } else {
                TransactionParticipant participant = new TransactionParticipant();
                participant.setTransaction(transaction);
                participant.setMember(share.member());
                participant.setShareAmount(share.shareAmount());
                transaction.getParticipants().add(participant);
            }
        }

        transaction.getParticipants().removeIf(
                participant -> !keptMemberIds.contains(participant.getMember().getId()));
    }

    private void reconcileDefinitionParticipants(RecurrenceDefinition definition, List<ResolvedParticipant> shares) {
        Map<Long, RecurrenceDefinitionParticipant> existingByMemberId = new LinkedHashMap<>();
        for (RecurrenceDefinitionParticipant participant : definition.getParticipants()) {
            existingByMemberId.put(participant.getMember().getId(), participant);
        }

        Set<Long> keptMemberIds = new HashSet<>();
        for (ResolvedParticipant share : shares) {
            keptMemberIds.add(share.member().getId());
            RecurrenceDefinitionParticipant existing = existingByMemberId.get(share.member().getId());
            if (existing != null) {
                existing.setShareAmount(share.shareAmount());
            } else {
                RecurrenceDefinitionParticipant participant = new RecurrenceDefinitionParticipant();
                participant.setDefinition(definition);
                participant.setMember(share.member());
                participant.setShareAmount(share.shareAmount());
                definition.getParticipants().add(participant);
            }
        }

        definition.getParticipants().removeIf(
                participant -> !keptMemberIds.contains(participant.getMember().getId()));
    }

    @Transactional
    public int importFromNubankCsv(MultipartFile file, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());

        List<String> lines;
        try {
            lines = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
            ).lines().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the CSV file.", e);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<FinancialTransaction> transactions = new ArrayList<>();

        record ParsedRow(String externalId, LocalDate date, BigDecimal rawAmount, String description) {}
        List<ParsedRow> parsed = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank()) continue;

            String[] parts = line.split(",", 4);
            if (parts.length < 4) continue;

            try {
                parsed.add(new ParsedRow(
                        parts[2].trim(),
                        LocalDate.parse(parts[0].trim(), formatter),
                        new BigDecimal(parts[1].trim()),
                        parts[3].trim()
                ));
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Line " + (i + 1) + " of the CSV is invalid: " + e.getMessage());
            }
        }

        Set<String> existingIds = financialTransactionRepository.findExistingExternalIds(
                ctx.getPlan(), parsed.stream().map(ParsedRow::externalId).toList());

        for (ParsedRow row : parsed) {
            if (existingIds.contains(row.externalId())) continue;

            FinancialTransaction t = new FinancialTransaction();
            t.setPlan(ctx.getPlan());
            t.setCreatedBy(ctx.getUser());
            t.setExternalId(row.externalId());
            t.setStartDate(row.date());
            t.setAmount(row.rawAmount().abs());
            t.setType(row.rawAmount().compareTo(BigDecimal.ZERO) >= 0
                    ? FinancialTransactionType.CREDIT
                    : FinancialTransactionType.DEBIT);
            t.setDescription(row.description());

            transactions.add(t);
        }

        financialTransactionRepository.saveAll(transactions);
        return transactions.size();
    }
}
