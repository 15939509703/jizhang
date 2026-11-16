package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionEntryOutDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.dto.TransactionPageOutDTO;
import com.lhj.jizhang.user.dto.TransactionUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.mapper.ReimbursementMapper;
import com.lhj.jizhang.user.mapper.SavingsContributionMapper;
import com.lhj.jizhang.user.mapper.SavingsGoalMapper;
import com.lhj.jizhang.user.entity.ReimbursementEntity;
import com.lhj.jizhang.user.entity.SavingsContributionEntity;
import com.lhj.jizhang.user.entity.SavingsGoalEntity;
import com.lhj.jizhang.user.model.BookPermission;
import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.TransactionScope;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TransactionService {
    private static final Set<String> TRANSACTION_TYPES = Set.of("EXPENSE", "INCOME", "TRANSFER");
    private static final Set<String> STATUSES = Set.of("EFFECTIVE", "VOIDED", "REVERSED");
    private static final Set<String> SORT_OPTIONS = Set.of("AMOUNT_DESC");

    private final TransactionMapper transactionMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;
    private ReimbursementMapper reimbursementMapper;
    private SavingsContributionMapper savingsContributionMapper;
    private SavingsGoalMapper savingsGoalMapper;
    private BudgetAlertNotificationService budgetAlertNotificationService;

    /**
     * 初始化账单服务。
     *
     * @param transactionMapper 数据访问器
     * @param accountEntryMapper 数据访问器
     * @param accountMapper 数据访问器
     * @param categoryMapper 数据访问器
     * @param bookMapper 数据访问器
     * @param bookAccessService 关联服务
     */
    public TransactionService(
            TransactionMapper transactionMapper,
            AccountEntryMapper accountEntryMapper,
            AccountMapper accountMapper,
            CategoryMapper categoryMapper,
            BookMapper bookMapper,
            BookAccessService bookAccessService
    ) {
        this.transactionMapper = transactionMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 设置报销与储蓄关联数据访问器。
     *
     * @param reimbursementMapper 数据访问器
     * @param savingsContributionMapper 数据访问器
     * @param savingsGoalMapper 数据访问器
     */
    @Autowired(required = false)
    void setFollowupMappers(ReimbursementMapper reimbursementMapper,
                            SavingsContributionMapper savingsContributionMapper,
                            SavingsGoalMapper savingsGoalMapper) {
        this.reimbursementMapper = reimbursementMapper;
        this.savingsContributionMapper = savingsContributionMapper;
        this.savingsGoalMapper = savingsGoalMapper;
    }

    /**
     * 设置预算提醒通知服务。
     *
     * @param service 关联服务
     */
    @Autowired(required = false)
    void setBudgetAlertNotificationService(BudgetAlertNotificationService service) {
        this.budgetAlertNotificationService = service;
    }

    /**
     * 创建账单。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 账单输出对象
     */
    @Transactional
    public TransactionOutDTO create(Long userId, TransactionCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        validateInput(input);
        validateOriginal(input);
        Map<Long, AccountEntity> accounts = lockAccounts(input.bookId(), accountIds(input));
        TransactionEntity existing = findByRequestId(userId, input.bookId(), input.requestId());
        if (existing != null) {
            return toOutput(existing, loadEntries(List.of(existing.getId())).get(existing.getId()));
        }
        validateCategory(input);
        TransactionEntity transaction = buildTransaction(userId, input);
        TransactionEntity persisted = insertTransaction(transaction, userId, input);
        if (persisted != transaction) {
            return toOutput(persisted, loadEntries(List.of(persisted.getId())).get(persisted.getId()));
        }
        applyEntries(transaction, input, accounts, userId);
        if (budgetAlertNotificationService != null) budgetAlertNotificationService.onExpenseCreated(userId, transaction);
        return toOutput(transaction, loadEntries(List.of(transaction.getId())).get(transaction.getId()));
    }

    /**
     * 查询账单列表。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param type 类型
     * @param status 状态
     * @param categoryId 分类ID
     * @param accountId 账户ID
     * @param minAmount 最小金额
     * @param maxAmount 最大金额
     * @param keyword 搜索关键词
     * @param startAt 起始时间
     * @param endAt 结束时间
     * @param sortBy 排序字段
     * @param cursorId 分页游标ID
     * @param limit 分页条数
     * @return 账单分页结果
     */
    public TransactionPageOutDTO list(
            Long userId,
            Long bookId,
            String type,
            String status,
            Long categoryId,
            Long accountId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String keyword,
            Instant startAt,
            Instant endAt,
            String sortBy,
            Long cursorId,
            Integer limit
    ) {
        TransactionScope scope = bookAccessService.requireTransactionScope(userId, bookId);
        int pageSize = validateQuery(type, status, sortBy, limit);
        validateAmountRange(minAmount, maxAmount);
        var query = Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId)
                .last("LIMIT " + (pageSize + 1));
        if (scope == TransactionScope.OWN_CREATED) {
            query.eq(TransactionEntity::getCreatedUserId, userId);
        }
        if ("AMOUNT_DESC".equals(sortBy)) {
            query.orderByDesc(TransactionEntity::getAmount, TransactionEntity::getId);
        } else {
            query.orderByDesc(TransactionEntity::getId);
        }
        if (type != null) {
            query.eq(TransactionEntity::getTransactionType, type);
        } else {
            query.in(TransactionEntity::getTransactionType, TRANSACTION_TYPES);
        }
        if (status != null) {
            query.eq(TransactionEntity::getStatus, status);
        }
        if (categoryId != null) {
            query.eq(TransactionEntity::getCategoryId, categoryId);
        }
        if (accountId != null) {
            query.inSql(TransactionEntity::getId,
                    "SELECT transaction_id FROM fin_account_entry WHERE account_id = " + accountId);
        }
        if (minAmount != null) {
            query.ge(TransactionEntity::getAmount, minAmount);
        }
        if (maxAmount != null) {
            query.le(TransactionEntity::getAmount, maxAmount);
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim();
            query.and(wrapper -> wrapper.like(TransactionEntity::getTitle, normalized)
                    .or().like(TransactionEntity::getNote, normalized));
        }
        if (startAt != null) {
            query.ge(TransactionEntity::getHappenedAt, toUtc(startAt));
        }
        if (endAt != null) {
            query.lt(TransactionEntity::getHappenedAt, toUtc(endAt));
        }
        if (cursorId != null) {
            query.lt(TransactionEntity::getId, cursorId);
        }
        List<TransactionEntity> rows = transactionMapper.selectList(query);
        boolean hasMore = rows.size() > pageSize;
        List<TransactionEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        Map<Long, List<AccountEntryEntity>> entries = scope == TransactionScope.ALL
                ? loadEntries(page.stream().map(TransactionEntity::getId).toList()) : Map.of();
        List<TransactionOutDTO> items = page.stream()
                .map(transaction -> toVisibleOutput(transaction, entries.get(transaction.getId()), scope))
                .toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.getLast().getId() : null;
        return new TransactionPageOutDTO(items, nextCursor, hasMore);
    }

    /**
     * 查询账单。
     *
     * @param userId 用户ID
     * @param transactionId 账单ID
     * @return 账单输出对象
     */
    public TransactionOutDTO get(Long userId, Long transactionId) {
        TransactionEntity transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        TransactionScope scope = bookAccessService.requireTransactionScope(userId, transaction.getBookId());
        if (scope == TransactionScope.OWN_CREATED && !userId.equals(transaction.getCreatedUserId())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在", HttpStatus.NOT_FOUND);
        }
        List<AccountEntryEntity> entries = scope == TransactionScope.ALL
                ? loadEntries(List.of(transactionId)).get(transactionId) : List.of();
        return toVisibleOutput(transaction, entries, scope);
    }

    /**
     * 更新账单。
     *
     * @param userId 用户ID
     * @param transactionId 账单ID
     * @param input 请求参数
     * @return 账单输出对象
     */
    @Transactional
    public TransactionOutDTO update(Long userId, Long transactionId, TransactionUpdateInDTO input) {
        TransactionEntity transaction = transactionMapper.selectByIdForUpdate(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        rejectLoanTransaction(transaction);
        bookAccessService.requireWritable(userId, transaction.getBookId());
        bookAccessService.requirePermission(userId, transaction.getBookId(),
                userId.equals(transaction.getCreatedUserId())
                        ? BookPermission.EDIT_OWN_TRANSACTIONS : BookPermission.EDIT_ALL_TRANSACTIONS);
        if (!"EFFECTIVE".equals(transaction.getStatus()) || !input.version().equals(transaction.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "账单已发生变化，请刷新后重试");
        }
        TransactionCreateInDTO command = updateCommand(transaction, input);
        validateInput(command);
        validateOriginal(command);
        validateCategory(command);
        List<AccountEntryEntity> originals = originalEntries(transactionId);
        Set<Long> accountIds = originals.stream().map(AccountEntryEntity::getAccountId).collect(Collectors.toSet());
        accountIds.addAll(accountIds(command));
        Map<Long, AccountEntity> accounts = lockAccounts(transaction.getBookId(), accountIds);
        reverseEntries(transaction, originals, accounts, userId);
        accountEntryMapper.delete(Wrappers.<AccountEntryEntity>lambdaQuery()
                .eq(AccountEntryEntity::getTransactionId, transactionId));
        applyTransactionChanges(transaction, input, userId);
        transactionMapper.updateById(transaction);
        applyEntries(transaction, command, accounts, userId);
        return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
    }

    /**
     * 作废账单并冲回账户流水。
     *
     * @param userId 用户ID
     * @param transactionId 账单ID
     * @return 账单输出对象
     */
    @Transactional
    public TransactionOutDTO voidTransaction(Long userId, Long transactionId) {
        TransactionEntity transaction = transactionMapper.selectByIdForUpdate(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        rejectLoanTransaction(transaction);
        bookAccessService.requireWritable(userId, transaction.getBookId());
        bookAccessService.requireAction(userId, transaction.getBookId(), BookAction.BILLS_VOID);
        bookAccessService.requirePermission(userId, transaction.getBookId(),
                userId.equals(transaction.getCreatedUserId())
                        ? BookPermission.EDIT_OWN_TRANSACTIONS : BookPermission.EDIT_ALL_TRANSACTIONS);
        if ("VOIDED".equals(transaction.getStatus())) {
            return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
        }
        if (!"EFFECTIVE".equals(transaction.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前账单状态不允许作废");
        }
        validateFollowupVoid(transaction);
        List<AccountEntryEntity> originalEntries = originalEntries(transactionId);
        Map<Long, AccountEntity> accounts = lockAccounts(transaction.getBookId(), originalEntries.stream()
                .map(AccountEntryEntity::getAccountId).collect(Collectors.toSet()));
        reverseEntries(transaction, originalEntries, accounts, userId);
        transaction.setStatus("VOIDED");
        transaction.setVersion(transaction.getVersion() + 1);
        transaction.setModifier(String.valueOf(userId));
        transactionMapper.updateById(transaction);
        updateFollowupAfterVoid(transaction, userId);
        return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
    }

    /**
     * 校验关联报销记录是否允许原账单作废。
     *
     * @param transaction 账单实体
     */
    private void validateFollowupVoid(TransactionEntity transaction) {
        if (reimbursementMapper == null) return;
        ReimbursementEntity reimbursement = reimbursementMapper.selectOne(Wrappers.<ReimbursementEntity>lambdaQuery()
                .eq(ReimbursementEntity::getExpenseTransactionId, transaction.getId())
                .in(ReimbursementEntity::getStatus, "PENDING", "REIMBURSED"));
        if (reimbursement != null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "请先取消报销项目再作废原支出");
        }
    }

    /** 借还本金流水属于借还记录，不能通过普通账单入口独立修改或作废。 */
    private void rejectLoanTransaction(TransactionEntity transaction) {
        if ("BORROW".equals(transaction.getTransactionType()) || "LEND".equals(transaction.getTransactionType())
                || "REPAYMENT".equals(transaction.getTransactionType())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "借还流水不能通过账单接口修改或作废");
        }
    }

    /**
     * 同步作废账单关联的报销和储蓄记录。
     *
     * @param transaction 账单实体
     * @param userId 用户ID
     */
    private void updateFollowupAfterVoid(TransactionEntity transaction, Long userId) {
        if (reimbursementMapper != null) {
            ReimbursementEntity reimbursement = reimbursementMapper.selectOne(Wrappers.<ReimbursementEntity>lambdaQuery()
                    .eq(ReimbursementEntity::getReimbursementTransactionId, transaction.getId())
                    .eq(ReimbursementEntity::getStatus, "REIMBURSED"));
            if (reimbursement != null) {
                reimbursement.setReimbursementTransactionId(null);
                reimbursement.setReimbursedTime(null);
                reimbursement.setStatus("PENDING");
                reimbursement.setVersion(reimbursement.getVersion() + 1);
                reimbursement.setModifier(String.valueOf(userId));
                reimbursementMapper.updateById(reimbursement);
            }
        }
        if (savingsContributionMapper != null) {
            SavingsContributionEntity contribution = savingsContributionMapper.selectOne(
                    Wrappers.<SavingsContributionEntity>lambdaQuery()
                            .eq(SavingsContributionEntity::getTransactionId, transaction.getId())
                            .eq(SavingsContributionEntity::getStatus, "EFFECTIVE"));
            if (contribution != null) {
                contribution.setStatus("VOIDED");
                contribution.setModifier(String.valueOf(userId));
                savingsContributionMapper.updateById(contribution);
                SavingsGoalEntity goal = savingsGoalMapper.selectById(contribution.getGoalId());
                BigDecimal completed = goal.getTargetAccountId() == null
                        ? goal.getInitialAmount().add(savingsContributionMapper.sumEffective(goal.getId()))
                        : accountMapper.selectById(goal.getTargetAccountId()).getCurrentBalance();
                if ("COMPLETED".equals(goal.getStatus()) && completed.compareTo(goal.getTargetAmount()) < 0) {
                    goal.setStatus("ACTIVE");
                    goal.setVersion(goal.getVersion() + 1);
                    goal.setModifier(String.valueOf(userId));
                    savingsGoalMapper.updateById(goal);
                }
            }
        }
    }

    /**
     * 校验输入。
     *
     * @param input 请求参数
     */
    private void validateInput(TransactionCreateInDTO input) {
        if ("TRANSFER".equals(input.transactionType())) {
            if (input.targetAccountId() == null || input.accountId().equals(input.targetAccountId())) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "转账必须选择不同的转入账户");
            }
            if (input.categoryId() != null) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "转账不能选择收支分类");
            }
        } else if (input.categoryId() == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "收入和支出必须选择分类");
        }
    }

    /**
     * 校验查询条件。
     *
     * @param type 类型
     * @param status 状态
     * @param sortBy 排序字段
     * @param limit 分页条数
     * @return 有效分页条数
     */
    private int validateQuery(String type, String status, String sortBy, Integer limit) {
        if (type != null && !TRANSACTION_TYPES.contains(type)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账单类型不正确");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账单状态不正确");
        }
        if (sortBy != null && !SORT_OPTIONS.contains(sortBy)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "排序方式不正确");
        }
        int pageSize = limit == null ? 20 : limit;
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "每页数量必须在1到100之间");
        }
        return pageSize;
    }

    /**
     * 校验金额范围。
     *
     * @param minAmount 最小金额
     * @param maxAmount 最大金额
     */
    private void validateAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) < 0
                || maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "筛选金额不能小于0");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "最小金额不能大于最大金额");
        }
    }

    /**
     * 将账单更新参数转换为创建参数。
     *
     * @param transaction 账单实体
     * @param input 请求参数
     * @return 账单请求参数
     */
    private TransactionCreateInDTO updateCommand(TransactionEntity transaction, TransactionUpdateInDTO input) {
        return new TransactionCreateInDTO(transaction.getRequestId(), transaction.getBookId(),
                input.transactionType(), input.categoryId(), transaction.getOriginalTransactionId(),
                input.accountId(), input.targetAccountId(), input.amount(), input.happenedAt(),
                input.title(), input.note());
    }

    /**
     * 将更新参数应用到账单实体。
     *
     * @param transaction 账单实体
     * @param input 请求参数
     * @param userId 用户ID
     */
    private void applyTransactionChanges(TransactionEntity transaction, TransactionUpdateInDTO input, Long userId) {
        transaction.setTransactionType(input.transactionType());
        transaction.setCategoryId(input.categoryId());
        transaction.setAmount(input.amount());
        transaction.setHappenedAt(toUtc(input.happenedAt()));
        transaction.setTitle(input.title().trim());
        transaction.setNote(input.note());
        transaction.setVersion(transaction.getVersion() + 1);
        transaction.setModifier(String.valueOf(userId));
    }

    /**
     * 提取账单涉及的账户ID。
     *
     * @param input 请求参数
     * @return 账单列表
     */
    private Collection<Long> accountIds(TransactionCreateInDTO input) {
        List<Long> accountIds = new ArrayList<>();
        accountIds.add(input.accountId());
        if (input.targetAccountId() != null) {
            accountIds.add(input.targetAccountId());
        }
        return accountIds;
    }

    /**
     * 锁定账单涉及的账户并校验归属。
     *
     * @param bookId 账本ID
     * @param accountIds 账户ID集合
     * @return 账单映射
     */
    private Map<Long, AccountEntity> lockAccounts(Long bookId, Collection<Long> accountIds) {
        Map<Long, AccountEntity> accounts = new LinkedHashMap<>();
        accountIds.stream().distinct().sorted().forEach(accountId -> {
            AccountEntity account = accountMapper.selectByIdForUpdate(accountId);
            if (account == null || !bookId.equals(account.getBookId()) || account.getStatus() != 1
                    || account.getDeletedFlag() != 0) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
            }
            accounts.put(accountId, account);
        });
        return accounts;
    }

    /**
     * 校验分类。
     *
     * @param input 请求参数
     */
    private void validateCategory(TransactionCreateInDTO input) {
        if (input.categoryId() == null) {
            return;
        }
        CategoryEntity category = categoryMapper.selectById(input.categoryId());
        if (category == null || !input.bookId().equals(category.getBookId())
                || !input.transactionType().equals(category.getCategoryType())
                || category.getHiddenFlag() != 0 || category.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类不存在或与账单类型不匹配");
        }
    }

    /**
     * 校验原始账单引用关系。
     *
     * @param input 请求参数
     */
    private void validateOriginal(TransactionCreateInDTO input) {
        if (input.originalTransactionId() == null) {
            return;
        }
        TransactionEntity original = transactionMapper.selectById(input.originalTransactionId());
        if (original == null || !input.bookId().equals(original.getBookId())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "复制来源账单不存在或不属于当前账本");
        }
    }

    /**
     * 按幂等请求ID查找已有账单。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param requestId 幂等请求ID
     * @return 账单实体
     */
    private TransactionEntity findByRequestId(Long userId, Long bookId, String requestId) {
        return transactionMapper.selectOne(Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId)
                .eq(TransactionEntity::getCreatedUserId, userId)
                .eq(TransactionEntity::getRequestId, requestId));
    }

    /**
     * 构建账单。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 账单实体
     */
    private TransactionEntity buildTransaction(Long userId, TransactionCreateInDTO input) {
        BookEntity book = bookMapper.selectById(input.bookId());
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(BusinessIdGenerator.next("TXN_"));
        transaction.setRequestId(input.requestId());
        transaction.setBookId(input.bookId());
        transaction.setCreatedUserId(userId);
        transaction.setTransactionType(input.transactionType());
        transaction.setCategoryId(input.categoryId());
        transaction.setOriginalTransactionId(input.originalTransactionId());
        transaction.setAmount(input.amount());
        transaction.setCurrencyCode(book.getCurrencyCode());
        transaction.setHappenedAt(toUtc(input.happenedAt()));
        transaction.setTitle(input.title().trim());
        transaction.setNote(input.note());
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        transaction.setCreator(String.valueOf(userId));
        transaction.setModifier(String.valueOf(userId));
        return transaction;
    }

    /**
     * 写入账单。
     *
     * @param transaction 账单实体
     * @param userId 用户ID
     * @param input 请求参数
     * @return 账单实体
     */
    private TransactionEntity insertTransaction(
            TransactionEntity transaction,
            Long userId,
            TransactionCreateInDTO input
    ) {
        try {
            transactionMapper.insert(transaction);
            return transaction;
        } catch (DuplicateKeyException exception) {
            TransactionEntity existing = findByRequestId(userId, input.bookId(), input.requestId());
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    /**
     * 根据账单类型写入对应账户流水。
     *
     * @param transaction 账单实体
     * @param input 请求参数
     * @param accounts 账户集合
     * @param userId 用户ID
     */
    private void applyEntries(
            TransactionEntity transaction,
            TransactionCreateInDTO input,
            Map<Long, AccountEntity> accounts,
            Long userId
    ) {
        if ("INCOME".equals(input.transactionType())) {
            applyEntry(transaction, accounts.get(input.accountId()), input.amount(), "INCREASE", userId);
        } else {
            applyEntry(transaction, accounts.get(input.accountId()), input.amount().negate(), "DECREASE", userId);
        }
        if ("TRANSFER".equals(input.transactionType())) {
            applyEntry(transaction, accounts.get(input.targetAccountId()), input.amount(), "INCREASE", userId);
        }
    }

    /**
     * 写入单条账户流水并更新余额。
     *
     * @param transaction 账单实体
     * @param account 账户实体
     * @param signedAmount 带符号的流水金额
     * @param entryType 流水类型
     * @param userId 用户ID
     */
    private void applyEntry(
            TransactionEntity transaction,
            AccountEntity account,
            BigDecimal signedAmount,
            String entryType,
            Long userId
    ) {
        BigDecimal balanceBefore = account.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.add(signedAmount);
        accountEntryMapper.insert(buildEntry(transaction, account.getId(), entryType, signedAmount,
                balanceBefore, balanceAfter, userId));
        updateAccountBalance(account, balanceAfter, userId);
    }

    /**
     * 构建流水。
     *
     * @param transaction 账单实体
     * @param accountId 账户ID
     * @param entryType 流水类型
     * @param signedAmount 带符号的流水金额
     * @param balanceBefore 变动前余额
     * @param balanceAfter 变动后余额
     * @param userId 用户ID
     * @return 账单实体
     */
    private AccountEntryEntity buildEntry(
            TransactionEntity transaction,
            Long accountId,
            String entryType,
            BigDecimal signedAmount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            Long userId
    ) {
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setTransactionId(transaction.getId());
        entry.setAccountId(accountId);
        entry.setEntryType(entryType);
        entry.setSignedAmount(signedAmount);
        entry.setBalanceBefore(balanceBefore);
        entry.setBalanceAfter(balanceAfter);
        entry.setHappenedAt(transaction.getHappenedAt());
        entry.setCreator(String.valueOf(userId));
        entry.setModifier(String.valueOf(userId));
        return entry;
    }

    /**
     * 更新账户余额。
     *
     * @param account 账户实体
     * @param balanceAfter 变动后余额
     * @param userId 用户ID
     */
    private void updateAccountBalance(AccountEntity account, BigDecimal balanceAfter, Long userId) {
        account.setCurrentBalance(balanceAfter);
        account.setVersion(account.getVersion() + 1);
        account.setModifier(String.valueOf(userId));
        accountMapper.updateById(account);
    }

    /**
     * 查询原账单关联的账户流水。
     *
     * @param transactionId 账单ID
     * @return 账单列表
     */
    private List<AccountEntryEntity> originalEntries(Long transactionId) {
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                .eq(AccountEntryEntity::getTransactionId, transactionId)
                .ne(AccountEntryEntity::getEntryType, "REVERSAL")
                .orderByAsc(AccountEntryEntity::getId));
    }

    /**
     * 冲回原账单的账户流水。
     *
     * @param transaction 账单实体
     * @param entries 流水集合
     * @param accounts 账户集合
     * @param userId 用户ID
     */
    private void reverseEntries(
            TransactionEntity transaction,
            List<AccountEntryEntity> entries,
            Map<Long, AccountEntity> accounts,
            Long userId
    ) {
        for (AccountEntryEntity original : entries) {
            AccountEntity account = accounts.get(original.getAccountId());
            BigDecimal reversalAmount = original.getSignedAmount().negate();
            applyEntry(transaction, account, reversalAmount, "REVERSAL", userId);
        }
    }

    /**
     * 加载流水。
     *
     * @param transactionIds 账单ID集合
     * @return 账单映射
     */
    private Map<Long, List<AccountEntryEntity>> loadEntries(List<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                        .in(AccountEntryEntity::getTransactionId, transactionIds)
                        .orderByAsc(AccountEntryEntity::getId)).stream()
                .collect(Collectors.groupingBy(AccountEntryEntity::getTransactionId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * 转换为账单输出对象。
     *
     * @param transaction 账单实体
     * @param entries 流水集合
     * @return 账单输出对象
     */
    private TransactionOutDTO toOutput(TransactionEntity transaction, List<AccountEntryEntity> entries) {
        List<TransactionEntryOutDTO> entryOutputs = entries == null ? List.of() : entries.stream()
                .sorted(Comparator.comparing(AccountEntryEntity::getId))
                .map(entry -> new TransactionEntryOutDTO(entry.getId(), entry.getAccountId(), entry.getEntryType(),
                        entry.getSignedAmount(), entry.getBalanceBefore(), entry.getBalanceAfter()))
                .toList();
        return new TransactionOutDTO(transaction.getId(), transaction.getTransactionNo(), transaction.getRequestId(),
                transaction.getBookId(), transaction.getCreatedUserId(), transaction.getTransactionType(),
                transaction.getCategoryId(), transaction.getOriginalTransactionId(), transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getHappenedAt().toInstant(ZoneOffset.UTC), transaction.getTitle(), transaction.getNote(),
                transaction.getStatus(), transaction.getVersion(), entryOutputs, List.of());
    }

    /** 受限成员仅能读取本人账单的非余额字段，且不暴露其他账单引用。 */
    private TransactionOutDTO toVisibleOutput(TransactionEntity transaction, List<AccountEntryEntity> entries,
                                              TransactionScope scope) {
        TransactionOutDTO output = toOutput(transaction, entries);
        if (scope == TransactionScope.ALL) {
            return output;
        }
        return new TransactionOutDTO(output.id(), output.transactionNo(), output.requestId(), output.bookId(),
                output.createdUserId(), output.transactionType(), output.categoryId(), null, output.amount(),
                output.currencyCode(), output.happenedAt(), output.title(), output.note(), output.status(),
                output.version(), List.of(), output.attachments());
    }

    /**
     * 将时间转换为 UTC 时间。
     *
     * @param instant 待转换时间戳
     * @return 转换后的时间
     */
    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
