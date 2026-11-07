package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.BookMenu;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.SavingsContributionInDTO;
import com.lhj.jizhang.user.dto.SavingsContributionOutDTO;
import com.lhj.jizhang.user.dto.SavingsGoalCreateInDTO;
import com.lhj.jizhang.user.dto.SavingsGoalOutDTO;
import com.lhj.jizhang.user.dto.SavingsGoalUpdateInDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.SavingsContributionEntity;
import com.lhj.jizhang.user.entity.SavingsGoalEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.SavingsContributionMapper;
import com.lhj.jizhang.user.mapper.SavingsGoalMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Service
public class SavingsGoalService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "PAUSED", "COMPLETED", "CLOSED");
    private final SavingsGoalMapper goalMapper;
    private final SavingsContributionMapper contributionMapper;
    private final AccountMapper accountMapper;
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;
    private final AccountService accountService;
    private final BookAccessService bookAccessService;

    public SavingsGoalService(SavingsGoalMapper goalMapper, SavingsContributionMapper contributionMapper,
                              AccountMapper accountMapper, TransactionService transactionService,
                              TransactionMapper transactionMapper, AccountService accountService,
                              BookAccessService bookAccessService) {
        this.goalMapper = goalMapper;
        this.contributionMapper = contributionMapper;
        this.accountMapper = accountMapper;
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
        this.accountService = accountService;
        this.bookAccessService = bookAccessService;
    }

    public List<SavingsGoalOutDTO> list(Long userId, Long bookId, String status) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.SAVINGS_GOALS);
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "储蓄目标状态不正确");
        }
        var query = Wrappers.<SavingsGoalEntity>lambdaQuery().eq(SavingsGoalEntity::getBookId, bookId)
                .eq(SavingsGoalEntity::getDeletedFlag, 0).orderByDesc(SavingsGoalEntity::getId);
        return goalMapper.selectList(query).stream().map(goal -> toOutput(goal, false))
                .filter(goal -> status == null || status.equals(goal.status())).toList();
    }

    public SavingsGoalOutDTO get(Long userId, Long id) {
        SavingsGoalEntity goal = requireGoal(id);
        bookAccessService.requireMenu(userId, goal.getBookId(), BookMenu.SAVINGS_GOALS);
        return toOutput(goal, true);
    }

    @Transactional
    public SavingsGoalOutDTO create(Long userId, SavingsGoalCreateInDTO input) {
        bookAccessService.requireAction(userId, input.bookId(), BookAction.SAVINGS_GOALS_MANAGE);
        validateDates(input.startDate(), input.targetDate());
        validateAccount(input.bookId(), input.targetAccountId());
        SavingsGoalEntity goal = new SavingsGoalEntity();
        goal.setGoalNo(BusinessIdGenerator.next("SVG_"));
        goal.setBookId(input.bookId());
        goal.setName(input.name().trim());
        goal.setDescription(trim(input.description()));
        goal.setTargetAmount(input.targetAmount());
        goal.setInitialAmount(input.initialAmount() == null ? BigDecimal.ZERO : input.initialAmount());
        goal.setTargetAccountId(input.targetAccountId());
        goal.setStartDate(input.startDate());
        goal.setTargetDate(input.targetDate());
        goal.setStatus(completed(goal).compareTo(goal.getTargetAmount()) >= 0 ? "COMPLETED" : "ACTIVE");
        goal.setVersion(0);
        goal.setDeletedFlag(0);
        goal.setCreator(String.valueOf(userId));
        goal.setModifier(String.valueOf(userId));
        goalMapper.insert(goal);
        return toOutput(goal, true);
    }

    @Transactional
    public SavingsGoalOutDTO update(Long userId, Long id, SavingsGoalUpdateInDTO input) {
        SavingsGoalEntity goal = requireLockedGoal(id);
        bookAccessService.requireAction(userId, goal.getBookId(), BookAction.SAVINGS_GOALS_MANAGE);
        validateVersion(goal, input.version());
        validateDates(input.startDate(), input.targetDate());
        validateAccount(goal.getBookId(), input.targetAccountId());
        applyUpdate(goal, input, userId);
        goalMapper.updateById(goal);
        return toOutput(goal, true);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        SavingsGoalEntity goal = requireLockedGoal(id);
        bookAccessService.requireAction(userId, goal.getBookId(), BookAction.SAVINGS_GOALS_MANAGE);
        goal.setDeletedFlag(1);
        goal.setStatus("CLOSED");
        goal.setVersion(goal.getVersion() + 1);
        goal.setModifier(String.valueOf(userId));
        goalMapper.updateById(goal);
    }

    @Transactional
    public SavingsGoalOutDTO addContribution(Long userId, Long id, SavingsContributionInDTO input) {
        SavingsGoalEntity goal = goalMapper.selectByIdForUpdate(id);
        if (goal == null || goal.getDeletedFlag() == 1) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "储蓄目标不存在");
        bookAccessService.requireAction(userId, goal.getBookId(), BookAction.SAVINGS_GOALS_CONTRIBUTE);
        if (!"ACTIVE".equals(goal.getStatus()) && !"COMPLETED".equals(goal.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前目标不能存入");
        }
        Long transactionId = null;
        String requestId = "GOAL_" + id + "_" + input.requestId();
        if (goal.getTargetAccountId() == null) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "请先为目标关联账户");
        }
        if ("TRANSFER".equals(input.contributionType())) {
            if (input.sourceAccountId() == null || input.happenedAt() == null) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "转账存入需设置来源账户、目标账户和发生时间");
            }
            TransactionOutDTO transaction = transactionService.create(userId, new TransactionCreateInDTO(
                    requestId, goal.getBookId(), "TRANSFER", null, null,
                    input.sourceAccountId(), goal.getTargetAccountId(), input.amount(), input.happenedAt(),
                    "存入目标：" + goal.getName(), input.note()));
            transactionId = transaction.id();
            SavingsContributionEntity existing = contributionMapper.selectOne(Wrappers.<SavingsContributionEntity>lambdaQuery()
                    .eq(SavingsContributionEntity::getTransactionId, transactionId));
            if (existing != null) return toOutput(goalMapper.selectById(id), true);
        } else {
            var transactionQuery = Wrappers.<TransactionEntity>lambdaQuery()
                    .eq(TransactionEntity::getBookId, goal.getBookId())
                    .eq(TransactionEntity::getCreatedUserId, userId)
                    .eq(TransactionEntity::getRequestId, requestId);
            TransactionEntity existingTransaction = transactionMapper.selectOne(transactionQuery);
            if (existingTransaction != null) return toOutput(goal, true);
            validateAccount(goal.getBookId(), goal.getTargetAccountId());
            accountService.deposit(userId, goal.getTargetAccountId(), input.amount(), requestId,
                    "存入目标：" + goal.getName());
            transactionId = transactionMapper.selectOne(transactionQuery).getId();
        }
        SavingsContributionEntity contribution = new SavingsContributionEntity();
        contribution.setContributionNo(BusinessIdGenerator.next("SVC_"));
        contribution.setGoalId(id);
        contribution.setTransactionId(transactionId);
        contribution.setContributionType(input.contributionType());
        contribution.setAmount(input.amount());
        contribution.setContributionDate(input.contributionDate());
        contribution.setStatus("EFFECTIVE");
        contribution.setNote(trim(input.note()));
        contribution.setCreator(String.valueOf(userId));
        contribution.setModifier(String.valueOf(userId));
        contributionMapper.insert(contribution);
        refreshCompletion(goal, userId);
        return toOutput(goal, true);
    }

    @Transactional
    public SavingsGoalOutDTO changeStatus(Long userId, Long id, String status) {
        if (!Set.of("ACTIVE", "PAUSED", "CLOSED").contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "目标状态不正确");
        }
        SavingsGoalEntity goal = goalMapper.selectByIdForUpdate(id);
        if (goal == null || goal.getDeletedFlag() == 1) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "储蓄目标不存在");
        bookAccessService.requireAction(userId, goal.getBookId(), BookAction.SAVINGS_GOALS_MANAGE);
        BigDecimal completed = completed(goal);
        goal.setStatus("ACTIVE".equals(status) && completed.compareTo(goal.getTargetAmount()) >= 0 ? "COMPLETED" : status);
        goal.setVersion(goal.getVersion() + 1);
        goal.setModifier(String.valueOf(userId));
        goalMapper.updateById(goal);
        return toOutput(goal, true);
    }

    @Transactional
    public SavingsGoalOutDTO removeManualContribution(Long userId, Long goalId, Long contributionId) {
        SavingsGoalEntity goal = goalMapper.selectByIdForUpdate(goalId);
        if (goal == null) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "储蓄目标不存在");
        bookAccessService.requireAction(userId, goal.getBookId(), BookAction.SAVINGS_GOALS_CONTRIBUTE);
        SavingsContributionEntity contribution = contributionMapper.selectById(contributionId);
        if (contribution == null || !goalId.equals(contribution.getGoalId()) || !"MANUAL".equals(contribution.getContributionType())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "只能撤销手工进度");
        }
        if (!"EFFECTIVE".equals(contribution.getStatus())) return toOutput(goal, true);
        if (contribution.getTransactionId() != null) {
            transactionService.voidTransaction(userId, contribution.getTransactionId());
            refreshCompletion(goal, userId);
            return toOutput(goal, true);
        }
        contribution.setStatus("VOIDED");
        contribution.setModifier(String.valueOf(userId));
        contributionMapper.updateById(contribution);
        refreshCompletion(goal, userId);
        return toOutput(goal, true);
    }

    private void refreshCompletion(SavingsGoalEntity goal, Long userId) {
        BigDecimal completed = completed(goal);
        if (!"CLOSED".equals(goal.getStatus()) && !"PAUSED".equals(goal.getStatus())) {
            goal.setStatus(completed.compareTo(goal.getTargetAmount()) >= 0 ? "COMPLETED" : "ACTIVE");
        }
        goal.setVersion(goal.getVersion() + 1);
        goal.setModifier(String.valueOf(userId));
        goalMapper.updateById(goal);
    }

    private void applyUpdate(SavingsGoalEntity goal, SavingsGoalUpdateInDTO input, Long userId) {
        goal.setName(input.name().trim());
        goal.setDescription(trim(input.description()));
        goal.setTargetAmount(input.targetAmount());
        goal.setTargetAccountId(input.targetAccountId());
        goal.setStartDate(input.startDate());
        goal.setTargetDate(input.targetDate());
        if ("ACTIVE".equals(goal.getStatus()) || "COMPLETED".equals(goal.getStatus())) {
            goal.setStatus(completed(goal).compareTo(goal.getTargetAmount()) >= 0 ? "COMPLETED" : "ACTIVE");
        }
        goal.setVersion(goal.getVersion() + 1);
        goal.setModifier(String.valueOf(userId));
    }

    private SavingsGoalOutDTO toOutput(SavingsGoalEntity goal, boolean includeContributions) {
        BigDecimal completed = completed(goal);
        BigDecimal remaining = goal.getTargetAmount().subtract(completed).max(BigDecimal.ZERO);
        BigDecimal rate = completed.divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP);
        List<SavingsContributionOutDTO> contributions = includeContributions
                ? contributionMapper.selectList(Wrappers.<SavingsContributionEntity>lambdaQuery()
                    .eq(SavingsContributionEntity::getGoalId, goal.getId()).orderByDesc(SavingsContributionEntity::getId))
                    .stream().map(this::toContribution).toList() : List.of();
        String status = goal.getTargetAccountId() != null && !"PAUSED".equals(goal.getStatus())
                && !"CLOSED".equals(goal.getStatus())
                ? completed.compareTo(goal.getTargetAmount()) >= 0 ? "COMPLETED" : "ACTIVE" : goal.getStatus();
        return new SavingsGoalOutDTO(goal.getId(), goal.getGoalNo(), goal.getBookId(), goal.getName(),
                goal.getDescription(), goal.getTargetAmount(), completed, remaining, rate,
                suggestion(remaining, goal.getTargetDate()), goal.getTargetAccountId(), goal.getStartDate(),
                goal.getTargetDate(), goal.getTargetDate() != null && goal.getTargetDate().isBefore(LocalDate.now())
                && completed.compareTo(goal.getTargetAmount()) < 0, status, goal.getVersion(), contributions);
    }

    private SavingsContributionOutDTO toContribution(SavingsContributionEntity item) {
        return new SavingsContributionOutDTO(item.getId(), item.getContributionNo(), item.getTransactionId(),
                item.getContributionType(), item.getAmount(), item.getContributionDate(), item.getStatus(), item.getNote());
    }

    private BigDecimal completed(SavingsGoalEntity goal) {
        if (goal.getTargetAccountId() != null) {
            AccountEntity account = accountMapper.selectById(goal.getTargetAccountId());
            return account.getCurrentBalance();
        }
        return goal.getInitialAmount().add(contributionMapper.sumEffective(goal.getId()));
    }

    private BigDecimal suggestion(BigDecimal remaining, LocalDate targetDate) {
        if (targetDate == null || remaining.signum() == 0) return null;
        YearMonth current = YearMonth.now();
        YearMonth target = YearMonth.from(targetDate);
        if (target.isBefore(current)) return remaining;
        long months = java.time.temporal.ChronoUnit.MONTHS.between(current, target) + 1;
        return remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.UP);
    }

    private SavingsGoalEntity requireGoal(Long id) {
        SavingsGoalEntity goal = goalMapper.selectById(id);
        if (goal == null || goal.getDeletedFlag() == 1) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "储蓄目标不存在");
        return goal;
    }

    private SavingsGoalEntity requireLockedGoal(Long id) {
        SavingsGoalEntity goal = goalMapper.selectByIdForUpdate(id);
        if (goal == null || goal.getDeletedFlag() == 1) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "储蓄目标不存在");
        }
        return goal;
    }

    private void validateVersion(SavingsGoalEntity goal, Integer version) {
        if (!version.equals(goal.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "储蓄目标已发生变化，请刷新后重试");
        }
    }

    private void validateAccount(Long bookId, Long accountId) {
        if (accountId == null) return;
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !bookId.equals(account.getBookId()) || account.getStatus() != 1
                || account.getDeletedFlag() != 0 || !"ASSET".equals(account.getAccountNature())) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "目标账户不可用");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate targetDate) {
        if (targetDate != null && targetDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "目标日期不能早于开始日期");
        }
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
