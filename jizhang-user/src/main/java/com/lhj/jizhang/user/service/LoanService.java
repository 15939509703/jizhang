package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.BookMenu;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.LoanAccountOutDTO;
import com.lhj.jizhang.user.dto.LoanCreateInDTO;
import com.lhj.jizhang.user.dto.LoanOutDTO;
import com.lhj.jizhang.user.dto.LoanPageOutDTO;
import com.lhj.jizhang.user.dto.LoanReminderInDTO;
import com.lhj.jizhang.user.dto.LoanRepaymentInDTO;
import com.lhj.jizhang.user.dto.LoanSummaryOutDTO;
import com.lhj.jizhang.user.dto.LoanUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.LoanRecordEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.LoanRecordMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class LoanService {
    private static final Set<String> LOAN_TYPES = Set.of("BORROW", "LEND");
    private static final Set<String> STATUSES = Set.of("OPEN", "PARTIAL", "CLOSED", "OVERDUE");

    private final LoanRecordMapper loanRecordMapper;
    private final BookAccessService bookAccessService;
    private final AccountMapper accountMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final BookMapper bookMapper;
    private final TransactionMapper transactionMapper;

    public LoanService(LoanRecordMapper loanRecordMapper, BookAccessService bookAccessService,
                       AccountMapper accountMapper, AccountEntryMapper accountEntryMapper,
                       BookMapper bookMapper, TransactionMapper transactionMapper) {
        this.loanRecordMapper = loanRecordMapper;
        this.bookAccessService = bookAccessService;
        this.accountMapper = accountMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.bookMapper = bookMapper;
        this.transactionMapper = transactionMapper;
    }

    /** 查询可用于借入、借出及归还的本账本有效账户。 */
    public List<LoanAccountOutDTO> accounts(Long userId, Long bookId) {
        var access = bookAccessService.myAccess(userId, bookId);
        var actions = access.actionPermissions();
        if (!Boolean.TRUE.equals(access.menuPermissions().get(BookMenu.LOANS.key()))
                || (!Boolean.TRUE.equals(actions.get(BookAction.LOANS_MANAGE.key()))
                && !Boolean.TRUE.equals(actions.get(BookAction.LOANS_REPAY.key())))) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "当前成员无账户选择权限");
        }
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getBookId, bookId)
                        .eq(AccountEntity::getStatus, 1)
                        .eq(AccountEntity::getDeletedFlag, 0)
                        .orderByAsc(AccountEntity::getSortNo, AccountEntity::getId)).stream()
                .map(account -> new LoanAccountOutDTO(account.getId(), account.getName())).toList();
    }

    @Transactional
    public LoanOutDTO create(Long userId, LoanCreateInDTO input) {
        bookAccessService.requireAction(userId, input.bookId(), BookAction.LOANS_MANAGE);
        if (input.accountId() == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借入或借出必须选择账户");
        }
        AccountEntity account = requireLoanAccount(input.bookId(), input.accountId());
        LoanRecordEntity loan = new LoanRecordEntity();
        loan.setLoanNo(BusinessIdGenerator.next("LOAN_"));
        loan.setBookId(input.bookId());
        loan.setCreatedUserId(userId);
        loan.setLoanType(input.loanType());
        loan.setCounterpartyName(input.counterpartyName().trim());
        loan.setTotalAmount(input.totalAmount());
        loan.setRepaidAmount(BigDecimal.ZERO);
        loan.setDueDate(input.dueDate());
        loan.setStatus("OPEN");
        loan.setNote(input.note());
        loan.setVersion(0);
        loan.setCreator(String.valueOf(userId));
        loan.setModifier(String.valueOf(userId));
        loan.setRelatedTransactionId(recordPrincipal(userId, input.bookId(), input.counterpartyName(),
                input.totalAmount(), input.note(), account, input.loanType()));
        loan.setAccountId(account.getId());
        loanRecordMapper.insert(loan);
        return toOutput(loan);
    }

    /** 锁定并校验借还关联账户，防止余额并发覆盖。 */
    private AccountEntity requireLoanAccount(Long bookId, Long accountId) {
        AccountEntity account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null || !bookId.equals(account.getBookId())
                || account.getStatus() != 1 || account.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
        }
        return account;
    }

    /** 写入借还本金交易与账户分录，并同步更新账户余额。 */
    private Long recordPrincipal(Long userId, Long bookId, String name, BigDecimal amount,
                                 String note, AccountEntity account, String loanType) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        TransactionEntity transaction = buildLoanTransaction(userId, bookId, name, amount, note, book, loanType);
        transactionMapper.insert(transaction);
        recordLoanEntry(userId, transaction, account, principalAmount(amount, loanType),
                "BORROW".equals(loanType) ? "INCREASE" : "DECREASE");
        return transaction.getId();
    }

    /** 记录本金、归还或冲回分录，并更新已锁定账户的余额。 */
    private void recordLoanEntry(Long userId, TransactionEntity transaction, AccountEntity account,
                                 BigDecimal signedAmount, String entryType) {
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setTransactionId(transaction.getId());
        entry.setAccountId(account.getId());
        entry.setEntryType(entryType);
        entry.setSignedAmount(signedAmount);
        entry.setBalanceBefore(account.getCurrentBalance());
        entry.setBalanceAfter(account.getCurrentBalance().add(signedAmount));
        entry.setHappenedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setCreator(String.valueOf(userId));
        entry.setModifier(String.valueOf(userId));
        accountEntryMapper.insert(entry);
        account.setCurrentBalance(entry.getBalanceAfter());
        account.setVersion(account.getVersion() + 1);
        account.setModifier(String.valueOf(userId));
        accountMapper.updateById(account);
    }

    /** 构造不计入日常收支统计的借还本金交易。 */
    private TransactionEntity buildLoanTransaction(Long userId, Long bookId, String name,
                                                   BigDecimal amount, String note, BookEntity book, String loanType) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(BusinessIdGenerator.next("TXN_"));
        transaction.setBookId(bookId);
        transaction.setCreatedUserId(userId);
        transaction.setTransactionType(loanType);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(book.getCurrencyCode());
        transaction.setHappenedAt(LocalDateTime.now(ZoneOffset.UTC));
        transaction.setTitle(principalTitle(loanType, name));
        transaction.setNote(note);
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        transaction.setCreator(String.valueOf(userId));
        transaction.setModifier(String.valueOf(userId));
        return transaction;
    }

    /** 借入本金为正，借出本金为负。 */
    private BigDecimal principalAmount(BigDecimal amount, String loanType) {
        return "BORROW".equals(loanType) ? amount : amount.negate();
    }

    /** 借还本金标题不超过交易表长度。 */
    private String principalTitle(String loanType, String name) {
        String title = ("BORROW".equals(loanType) ? "借入自" : "借出给") + name.trim();
        return title.length() > 128 ? title.substring(0, 128) : title;
    }

    public LoanPageOutDTO list(Long userId, Long bookId, String loanType, String status, Long cursorId, Integer limit) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.LOANS);
        int pageSize = validateListInput(loanType, status, limit);
        var query = Wrappers.<LoanRecordEntity>lambdaQuery()
                .eq(LoanRecordEntity::getBookId, bookId)
                .orderByDesc(LoanRecordEntity::getId)
                .last("LIMIT " + (pageSize + 1));
        if (loanType != null && !loanType.isBlank()) {
            query.eq(LoanRecordEntity::getLoanType, loanType);
        }
        if (status != null && !status.isBlank()) {
            query.eq(LoanRecordEntity::getStatus, status);
        }
        if (cursorId != null) {
            query.lt(LoanRecordEntity::getId, cursorId);
        }
        List<LoanRecordEntity> rows = loanRecordMapper.selectList(query);
        boolean hasMore = rows.size() > pageSize;
        List<LoanRecordEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        Long nextCursor = hasMore && !page.isEmpty() ? page.getLast().getId() : null;
        return new LoanPageOutDTO(page.stream().map(this::toOutput).toList(), nextCursor, hasMore);
    }

    public LoanSummaryOutDTO summary(Long userId, Long bookId) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.LOANS);
        List<LoanRecordEntity> rows = loanRecordMapper.selectList(Wrappers.<LoanRecordEntity>lambdaQuery()
                .eq(LoanRecordEntity::getBookId, bookId)
                .ne(LoanRecordEntity::getStatus, "CLOSED"));
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        for (LoanRecordEntity row : rows) {
            BigDecimal remaining = remaining(row);
            if ("BORROW".equals(row.getLoanType())) {
                payable = payable.add(remaining);
            } else {
                receivable = receivable.add(remaining);
            }
            if (isOverdue(row)) {
                overdue = overdue.add(remaining);
            }
        }
        return new LoanSummaryOutDTO(payable, receivable, overdue, rows.size());
    }

    public LoanOutDTO get(Long userId, Long id) {
        LoanRecordEntity loan = requireLoan(id);
        bookAccessService.requireMenu(userId, loan.getBookId(), BookMenu.LOANS);
        return toOutput(loan);
    }

    @Transactional
    public LoanOutDTO update(Long userId, Long id, LoanUpdateInDTO input) {
        LoanRecordEntity loan = loanRecordMapper.selectByIdForUpdate(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        bookAccessService.requireAction(userId, loan.getBookId(), BookAction.LOANS_MANAGE);
        if (!input.version().equals(loan.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "借还记录已变化，请刷新后重试");
        }
        if (input.totalAmount().compareTo(loan.getRepaidAmount()) < 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "总金额不能小于已还金额");
        }
        reconcilePrincipal(userId, loan, input);
        applyUpdate(userId, loan, input);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    /** 为历史借还补记本金，或冲回旧金额并按新账户重记。 */
    private void reconcilePrincipal(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        if (input.accountId() == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借入或借出必须选择账户");
        }
        if (loan.getRelatedTransactionId() != null && loan.getAccountId() == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "借还账户未关联，请检查历史流水");
        }
        if (Objects.equals(loan.getAccountId(), input.accountId())
                && input.totalAmount().compareTo(loan.getTotalAmount()) == 0) {
            updatePrincipalTransactionDetails(userId, loan, input);
            return;
        }
        TransactionEntity transaction = requirePrincipalTransaction(loan);
        Map<Long, AccountEntity> accounts = lockLoanAccounts(loan, input.accountId());
        if (transaction == null) {
            loan.setRelatedTransactionId(recordPrincipal(userId, loan.getBookId(), input.counterpartyName(),
                    input.totalAmount(), input.note(), accounts.get(input.accountId()), loan.getLoanType()));
        } else {
            adjustLinkedPrincipal(userId, loan, input, transaction, accounts);
        }
        loan.setAccountId(input.accountId());
    }

    /** 冲回原本金分录、按新账户和金额重记，并更新关联交易。 */
    private void adjustLinkedPrincipal(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input,
                                       TransactionEntity transaction, Map<Long, AccountEntity> accounts) {
        BigDecimal oldAmount = principalAmount(loan.getTotalAmount(), loan.getLoanType());
        BigDecimal newAmount = principalAmount(input.totalAmount(), loan.getLoanType());
        recordLoanEntry(userId, transaction, accounts.get(loan.getAccountId()), oldAmount.negate(), "REVERSAL");
        recordLoanEntry(userId, transaction, accounts.get(input.accountId()), newAmount,
                "BORROW".equals(loan.getLoanType()) ? "INCREASE" : "DECREASE");
        transaction.setAmount(input.totalAmount());
        transaction.setTitle(principalTitle(loan.getLoanType(), input.counterpartyName()));
        transaction.setNote(input.note());
        transaction.setVersion(transaction.getVersion() + 1);
        transaction.setModifier(String.valueOf(userId));
        transactionMapper.updateById(transaction);
    }

    /** 仅修改记录信息时同步本金流水标题和备注，不改变余额。 */
    private void updatePrincipalTransactionDetails(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        TransactionEntity transaction = requirePrincipalTransaction(loan);
        if (transaction == null) {
            return;
        }
        String title = principalTitle(loan.getLoanType(), input.counterpartyName());
        if (!title.equals(transaction.getTitle()) || !Objects.equals(input.note(), transaction.getNote())) {
            transaction.setTitle(title);
            transaction.setNote(input.note());
            transaction.setVersion(transaction.getVersion() + 1);
            transaction.setModifier(String.valueOf(userId));
            transactionMapper.updateById(transaction);
        }
    }

    /** 锁定原关联交易并验证其仍然是有效借还本金流水。 */
    private TransactionEntity requirePrincipalTransaction(LoanRecordEntity loan) {
        if (loan.getRelatedTransactionId() == null) {
            return null;
        }
        TransactionEntity transaction = transactionMapper.selectByIdForUpdate(loan.getRelatedTransactionId());
        if (transaction == null || !loan.getBookId().equals(transaction.getBookId())
                || !loan.getLoanType().equals(transaction.getTransactionType())
                || !"EFFECTIVE".equals(transaction.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "关联借还流水不存在或已失效");
        }
        return transaction;
    }

    /** 按 ID 顺序锁定新旧账户，已停用的原账户只允许冲回。 */
    private Map<Long, AccountEntity> lockLoanAccounts(LoanRecordEntity loan, Long targetAccountId) {
        Map<Long, AccountEntity> accounts = new HashMap<>();
        Stream.of(loan.getAccountId(), targetAccountId).filter(Objects::nonNull).distinct().sorted().forEach(id -> {
            AccountEntity account = accountMapper.selectByIdForUpdate(id);
            if (account == null || !loan.getBookId().equals(account.getBookId())) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不属于当前账本");
            }
            if (id.equals(targetAccountId) && (account.getStatus() != 1 || account.getDeletedFlag() != 0)) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "新关联账户不可用");
            }
            accounts.put(id, account);
        });
        return accounts;
    }

    /** 登记本次归还，并在选定账户记录借入扣款或借出收款。 */
    @Transactional
    public LoanOutDTO repay(Long userId, Long id, LoanRepaymentInDTO input) {
        LoanRecordEntity loan = loanRecordMapper.selectByIdForUpdate(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        bookAccessService.requireAction(userId, loan.getBookId(), BookAction.LOANS_REPAY);
        if ("CLOSED".equals(loan.getStatus())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "已结清记录不能继续还款");
        }
        if (input.amount() == null || input.amount().signum() <= 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "还款金额必须大于0");
        }
        BigDecimal nextRepaid = loan.getRepaidAmount().add(input.amount());
        if (nextRepaid.compareTo(loan.getTotalAmount()) > 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "还款金额不能超过剩余金额");
        }
        recordRepayment(userId, loan, input);
        loan.setRepaidAmount(nextRepaid);
        loan.setStatus(nextRepaid.compareTo(loan.getTotalAmount()) == 0 ? "CLOSED" : "PARTIAL");
        loan.setNote(appendTimeline(loan.getNote(), repaymentText(loan, input)));
        bumpVersion(userId, loan);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    /**
     * 为本次归还生成本金流水，并更新选定账户的余额。
     *
     * @param userId 操作用户 ID
     * @param loan 已锁定的借还记录
     * @param input 本次收回金额和备注
     */
    private void recordRepayment(Long userId, LoanRecordEntity loan, LoanRepaymentInDTO input) {
        if (loan.getAccountId() == null || loan.getRelatedTransactionId() == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "请先关联借还账户和本金流水后再登记归还");
        }
        TransactionEntity original = requirePrincipalTransaction(loan);
        Long accountId = input.accountId() == null ? loan.getAccountId() : input.accountId();
        AccountEntity account = requireLoanAccount(loan.getBookId(), accountId);
        TransactionEntity transaction = buildRepaymentTransaction(userId, loan, input, original);
        transactionMapper.insert(transaction);
        boolean borrowed = "BORROW".equals(loan.getLoanType());
        recordLoanEntry(userId, transaction, account, borrowed ? input.amount().negate() : input.amount(),
                borrowed ? "DECREASE" : "INCREASE");
    }

    /**
     * 构造不计入日常收支统计、关联原借还本金的归还交易。
     *
     * @param userId 操作用户 ID
     * @param loan 借还记录
     * @param input 本次收回金额和备注
     * @param original 原借出交易
     * @return 本次本金归还交易
     */
    private TransactionEntity buildRepaymentTransaction(Long userId, LoanRecordEntity loan,
                                                        LoanRepaymentInDTO input, TransactionEntity original) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(BusinessIdGenerator.next("TXN_"));
        transaction.setBookId(loan.getBookId());
        transaction.setCreatedUserId(userId);
        transaction.setTransactionType("REPAYMENT");
        transaction.setOriginalTransactionId(original.getId());
        transaction.setAmount(input.amount());
        transaction.setCurrencyCode(original.getCurrencyCode());
        transaction.setHappenedAt(LocalDateTime.now(ZoneOffset.UTC));
        String title = "BORROW".equals(loan.getLoanType())
                ? "归还" + loan.getCounterpartyName() + "的借款"
                : "收回" + loan.getCounterpartyName() + "的借款";
        transaction.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
        transaction.setNote(input.note());
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        transaction.setCreator(String.valueOf(userId));
        transaction.setModifier(String.valueOf(userId));
        return transaction;
    }

    @Transactional
    public LoanOutDTO remind(Long userId, Long id, LoanReminderInDTO input) {
        LoanRecordEntity loan = loanRecordMapper.selectByIdForUpdate(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        bookAccessService.requireAction(userId, loan.getBookId(), BookAction.LOANS_REMIND);
        if (!"LEND".equals(loan.getLoanType())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "只有借出记录需要催收");
        }
        if ("CLOSED".equals(loan.getStatus())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "已结清记录无需催收");
        }
        loan.setNote(appendTimeline(loan.getNote(), reminderText(input)));
        bumpVersion(userId, loan);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    /** 应用借还记录修改；编辑结清时校验归还权限并入账剩余本金。 */
    private void applyUpdate(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        loan.setCounterpartyName(input.counterpartyName().trim());
        loan.setTotalAmount(input.totalAmount());
        loan.setDueDate(input.dueDate());
        loan.setStatus(input.status());
        if ("CLOSED".equals(input.status())) {
            BigDecimal amount = input.totalAmount().subtract(loan.getRepaidAmount());
            if (amount.signum() > 0 && loan.getAccountId() != null) {
                bookAccessService.requireAction(userId, loan.getBookId(), BookAction.LOANS_REPAY);
                recordRepayment(userId, loan, new LoanRepaymentInDTO(amount, loan.getAccountId(), input.note()));
            }
            loan.setRepaidAmount(input.totalAmount());
        }
        loan.setNote(input.note());
        bumpVersion(userId, loan);
    }

    private int validateListInput(String loanType, String status, Integer limit) {
        if (loanType != null && !loanType.isBlank() && !LOAN_TYPES.contains(loanType)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还类型不正确");
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还状态不正确");
        }
        int pageSize = limit == null ? 20 : limit;
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "每页数量必须在1到100之间");
        }
        return pageSize;
    }

    private LoanRecordEntity requireLoan(Long id) {
        LoanRecordEntity loan = loanRecordMapper.selectById(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        return loan;
    }

    private void bumpVersion(Long userId, LoanRecordEntity loan) {
        loan.setVersion(loan.getVersion() + 1);
        loan.setModifier(String.valueOf(userId));
    }

    private String repaymentText(LoanRecordEntity loan, LoanRepaymentInDTO input) {
        String action = "BORROW".equals(loan.getLoanType()) ? "还款" : "收款";
        String suffix = input.note() == null || input.note().isBlank() ? "" : "，" + input.note().trim();
        return action + input.amount() + suffix;
    }

    private String reminderText(LoanReminderInDTO input) {
        String note = input.note() == null || input.note().isBlank() ? "已催收" : input.note().trim();
        return "催收：" + note;
    }

    private String appendTimeline(String note, String event) {
        String line = "[" + LocalDate.now() + "] " + event;
        if (note == null || note.isBlank()) {
            return line;
        }
        String next = note + "\n" + line;
        return next.length() > 1000 ? next.substring(next.length() - 1000) : next;
    }

    public LoanOutDTO toOutput(LoanRecordEntity loan) {
        return new LoanOutDTO(loan.getId(), loan.getLoanNo(), loan.getBookId(), loan.getCreatedUserId(),
                loan.getLoanType(), loan.getCounterpartyName(), loan.getTotalAmount(), loan.getRepaidAmount(),
                remaining(loan), loan.getDueDate(), displayStatus(loan), isOverdue(loan),
                loan.getRelatedTransactionId(), loan.getAccountId(), loan.getNote(), loan.getVersion(),
                toInstant(loan.getModifiedTime()));
    }

    private BigDecimal remaining(LoanRecordEntity loan) {
        return loan.getTotalAmount().subtract(loan.getRepaidAmount());
    }

    private boolean isOverdue(LoanRecordEntity loan) {
        return loan.getDueDate() != null && !"CLOSED".equals(loan.getStatus())
                && loan.getDueDate().isBefore(LocalDate.now());
    }

    private String displayStatus(LoanRecordEntity loan) {
        return isOverdue(loan) ? "OVERDUE" : loan.getStatus();
    }

    private Instant toInstant(java.time.LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
