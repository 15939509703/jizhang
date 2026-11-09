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

    /** 查询可用于借出的本账本有效账户。 */
    public List<LoanAccountOutDTO> accounts(Long userId, Long bookId) {
        bookAccessService.requireAction(userId, bookId, BookAction.LOANS_MANAGE);
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
        if ("LEND".equals(input.loanType()) && input.accountId() == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借出必须选择账户");
        }
        AccountEntity account = "LEND".equals(input.loanType()) ? requireLendAccount(input) : null;
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
        if (account != null) {
            loan.setRelatedTransactionId(recordLend(userId, input.bookId(), input.counterpartyName(),
                    input.totalAmount(), input.note(), account));
            loan.setAccountId(account.getId());
        }
        loanRecordMapper.insert(loan);
        return toOutput(loan);
    }

    /** 锁定并校验借出账户，防止余额并发覆盖。 */
    private AccountEntity requireLendAccount(LoanCreateInDTO input) {
        AccountEntity account = accountMapper.selectByIdForUpdate(input.accountId());
        if (account == null || !input.bookId().equals(account.getBookId())
                || account.getStatus() != 1 || account.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
        }
        return account;
    }

    /** 写入借出交易与账户分录，并同步更新账户余额。 */
    private Long recordLend(Long userId, Long bookId, String name, BigDecimal amount,
                            String note, AccountEntity account) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        TransactionEntity transaction = buildLendTransaction(userId, bookId, name, amount, note, book);
        transactionMapper.insert(transaction);
        recordLendEntry(userId, transaction, account, amount.negate(), "DECREASE");
        return transaction.getId();
    }

    /** 记录借出、收回或冲回分录，并更新已锁定账户的余额。 */
    private void recordLendEntry(Long userId, TransactionEntity transaction, AccountEntity account,
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

    /** 构造不计入日常收支统计的借出交易。 */
    private TransactionEntity buildLendTransaction(Long userId, Long bookId, String name,
                                                   BigDecimal amount, String note, BookEntity book) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(BusinessIdGenerator.next("TXN_"));
        transaction.setBookId(bookId);
        transaction.setCreatedUserId(userId);
        transaction.setTransactionType("LEND");
        transaction.setAmount(amount);
        transaction.setCurrencyCode(book.getCurrencyCode());
        transaction.setHappenedAt(LocalDateTime.now(ZoneOffset.UTC));
        transaction.setTitle("借出给" + name.trim());
        transaction.setNote(note);
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        transaction.setCreator(String.valueOf(userId));
        transaction.setModifier(String.valueOf(userId));
        return transaction;
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
        reconcileLend(userId, loan, input);
        applyUpdate(userId, loan, input);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    /** 将历史借出补扣，或对已扣款记录冲回旧额并扣除新额。 */
    private void reconcileLend(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        if (!"LEND".equals(loan.getLoanType())) {
            return;
        }
        if (input.accountId() == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借出必须选择账户");
        }
        if (loan.getRelatedTransactionId() != null && loan.getAccountId() == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "借出账户未关联，请检查历史流水");
        }
        if (Objects.equals(loan.getAccountId(), input.accountId())
                && input.totalAmount().compareTo(loan.getTotalAmount()) == 0) {
            updateLendTransactionDetails(userId, loan, input);
            return;
        }
        TransactionEntity transaction = requireLendTransaction(loan);
        Map<Long, AccountEntity> accounts = lockLendAccounts(loan, input.accountId());
        if (transaction == null) {
            loan.setRelatedTransactionId(recordLend(userId, loan.getBookId(), input.counterpartyName(),
                    input.totalAmount(), input.note(), accounts.get(input.accountId())));
        } else {
            adjustLinkedLend(userId, loan, input, transaction, accounts);
        }
        loan.setAccountId(input.accountId());
    }

    /** 冲回旧账户扣款、扣除新借出金额并更新关联交易。 */
    private void adjustLinkedLend(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input,
                                  TransactionEntity transaction, Map<Long, AccountEntity> accounts) {
        recordLendEntry(userId, transaction, accounts.get(loan.getAccountId()), loan.getTotalAmount(), "REVERSAL");
        recordLendEntry(userId, transaction, accounts.get(input.accountId()), input.totalAmount().negate(), "DECREASE");
        transaction.setAmount(input.totalAmount());
        transaction.setTitle("借出给" + input.counterpartyName().trim());
        transaction.setNote(input.note());
        transaction.setVersion(transaction.getVersion() + 1);
        transaction.setModifier(String.valueOf(userId));
        transactionMapper.updateById(transaction);
    }

    /** 仅修改记录信息时同步账户流水标题和备注，不改变余额。 */
    private void updateLendTransactionDetails(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        TransactionEntity transaction = requireLendTransaction(loan);
        if (transaction == null) {
            return;
        }
        String title = "借出给" + input.counterpartyName().trim();
        if (!title.equals(transaction.getTitle()) || !Objects.equals(input.note(), transaction.getNote())) {
            transaction.setTitle(title);
            transaction.setNote(input.note());
            transaction.setVersion(transaction.getVersion() + 1);
            transaction.setModifier(String.valueOf(userId));
            transactionMapper.updateById(transaction);
        }
    }

    /** 锁定原关联交易并验证其仍然是有效借出流水。 */
    private TransactionEntity requireLendTransaction(LoanRecordEntity loan) {
        if (loan.getRelatedTransactionId() == null) {
            return null;
        }
        TransactionEntity transaction = transactionMapper.selectByIdForUpdate(loan.getRelatedTransactionId());
        if (transaction == null || !loan.getBookId().equals(transaction.getBookId())
                || !"LEND".equals(transaction.getTransactionType()) || !"EFFECTIVE".equals(transaction.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "关联借出流水不存在或已失效");
        }
        return transaction;
    }

    /** 按 ID 顺序锁定新旧账户，已停用的原账户只允许冲回。 */
    private Map<Long, AccountEntity> lockLendAccounts(LoanRecordEntity loan, Long targetAccountId) {
        Map<Long, AccountEntity> accounts = new HashMap<>();
        Stream.of(loan.getAccountId(), targetAccountId).filter(Objects::nonNull).distinct().sorted().forEach(id -> {
            AccountEntity account = accountMapper.selectByIdForUpdate(id);
            if (account == null || !loan.getBookId().equals(account.getBookId())) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不属于当前账本");
            }
            if (id.equals(targetAccountId) && (account.getStatus() != 1 || account.getDeletedFlag() != 0)) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "新借出账户不可用");
            }
            accounts.put(id, account);
        });
        return accounts;
    }

    /** 登记本次还款；借出收回同时入账到关联账户，失败时整笔回滚。 */
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
        recordLendRepayment(userId, loan, input);
        loan.setRepaidAmount(nextRepaid);
        loan.setStatus(nextRepaid.compareTo(loan.getTotalAmount()) == 0 ? "CLOSED" : "PARTIAL");
        loan.setNote(appendTimeline(loan.getNote(), repaymentText(loan, input)));
        bumpVersion(userId, loan);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    /**
     * 为本次借出收回生成独立本金流水，并增加已锁定关联账户的余额。
     *
     * @param userId 操作用户 ID
     * @param loan 已锁定的借还记录
     * @param input 本次收回金额和备注
     */
    private void recordLendRepayment(Long userId, LoanRecordEntity loan, LoanRepaymentInDTO input) {
        if (!"LEND".equals(loan.getLoanType())) {
            return;
        }
        if (loan.getAccountId() == null || loan.getRelatedTransactionId() == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "请先关联借出账户和借出流水后再登记收回");
        }
        TransactionEntity original = requireLendTransaction(loan);
        AccountEntity account = accountMapper.selectByIdForUpdate(loan.getAccountId());
        if (account == null || !loan.getBookId().equals(account.getBookId())
                || account.getStatus() != 1 || account.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "关联借出账户不存在或不可用，请先修改借出账户");
        }
        TransactionEntity transaction = buildLendRepaymentTransaction(userId, loan, input, original);
        transactionMapper.insert(transaction);
        recordLendEntry(userId, transaction, account, input.amount(), "INCREASE");
    }

    /**
     * 构造不计入日常收入统计、关联原借出的本金收回交易。
     *
     * @param userId 操作用户 ID
     * @param loan 借还记录
     * @param input 本次收回金额和备注
     * @param original 原借出交易
     * @return 本次本金收回交易
     */
    private TransactionEntity buildLendRepaymentTransaction(Long userId, LoanRecordEntity loan,
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
        String title = "收回" + loan.getCounterpartyName() + "的借款";
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

    /** 应用借还记录修改；编辑结清时校验收款权限并入账剩余借出本金。 */
    private void applyUpdate(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        loan.setCounterpartyName(input.counterpartyName().trim());
        loan.setTotalAmount(input.totalAmount());
        loan.setDueDate(input.dueDate());
        loan.setStatus(input.status());
        if ("CLOSED".equals(input.status())) {
            BigDecimal amount = input.totalAmount().subtract(loan.getRepaidAmount());
            if ("LEND".equals(loan.getLoanType()) && amount.signum() > 0) {
                bookAccessService.requireAction(userId, loan.getBookId(), BookAction.LOANS_REPAY);
                recordLendRepayment(userId, loan, new LoanRepaymentInDTO(amount, input.note()));
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
