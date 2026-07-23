package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.LoanCreateInDTO;
import com.lhj.jizhang.user.dto.LoanOutDTO;
import com.lhj.jizhang.user.dto.LoanPageOutDTO;
import com.lhj.jizhang.user.dto.LoanReminderInDTO;
import com.lhj.jizhang.user.dto.LoanRepaymentInDTO;
import com.lhj.jizhang.user.dto.LoanSummaryOutDTO;
import com.lhj.jizhang.user.dto.LoanUpdateInDTO;
import com.lhj.jizhang.user.entity.LoanRecordEntity;
import com.lhj.jizhang.user.mapper.LoanRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
public class LoanService {
    private static final Set<String> LOAN_TYPES = Set.of("BORROW", "LEND");
    private static final Set<String> STATUSES = Set.of("OPEN", "PARTIAL", "CLOSED", "OVERDUE");

    private final LoanRecordMapper loanRecordMapper;
    private final BookAccessService bookAccessService;

    public LoanService(LoanRecordMapper loanRecordMapper, BookAccessService bookAccessService) {
        this.loanRecordMapper = loanRecordMapper;
        this.bookAccessService = bookAccessService;
    }

    @Transactional
    public LoanOutDTO create(Long userId, LoanCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
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
        loanRecordMapper.insert(loan);
        return toOutput(loan);
    }

    public LoanPageOutDTO list(Long userId, Long bookId, String loanType, String status, Long cursorId, Integer limit) {
        bookAccessService.requireMember(userId, bookId);
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
        bookAccessService.requireMember(userId, bookId);
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
        bookAccessService.requireMember(userId, loan.getBookId());
        return toOutput(loan);
    }

    @Transactional
    public LoanOutDTO update(Long userId, Long id, LoanUpdateInDTO input) {
        LoanRecordEntity loan = loanRecordMapper.selectByIdForUpdate(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        bookAccessService.requireWritable(userId, loan.getBookId());
        if (!input.version().equals(loan.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "借还记录已变化，请刷新后重试");
        }
        if (input.totalAmount().compareTo(loan.getRepaidAmount()) < 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "总金额不能小于已还金额");
        }
        applyUpdate(userId, loan, input);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    @Transactional
    public LoanOutDTO repay(Long userId, Long id, LoanRepaymentInDTO input) {
        LoanRecordEntity loan = loanRecordMapper.selectByIdForUpdate(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        bookAccessService.requireWritable(userId, loan.getBookId());
        if ("CLOSED".equals(loan.getStatus())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "已结清记录不能继续还款");
        }
        BigDecimal nextRepaid = loan.getRepaidAmount().add(input.amount());
        if (nextRepaid.compareTo(loan.getTotalAmount()) > 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "还款金额不能超过剩余金额");
        }
        loan.setRepaidAmount(nextRepaid);
        loan.setStatus(nextRepaid.compareTo(loan.getTotalAmount()) == 0 ? "CLOSED" : "PARTIAL");
        loan.setNote(appendTimeline(loan.getNote(), repaymentText(loan, input)));
        bumpVersion(userId, loan);
        loanRecordMapper.updateById(loan);
        return toOutput(loan);
    }

    @Transactional
    public LoanOutDTO remind(Long userId, Long id, LoanReminderInDTO input) {
        LoanRecordEntity loan = loanRecordMapper.selectByIdForUpdate(id);
        if (loan == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "借还记录不存在");
        }
        bookAccessService.requireWritable(userId, loan.getBookId());
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

    private void applyUpdate(Long userId, LoanRecordEntity loan, LoanUpdateInDTO input) {
        loan.setCounterpartyName(input.counterpartyName().trim());
        loan.setTotalAmount(input.totalAmount());
        loan.setDueDate(input.dueDate());
        loan.setStatus(input.status());
        if ("CLOSED".equals(input.status())) {
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
                loan.getRelatedTransactionId(), loan.getNote(), loan.getVersion(), toInstant(loan.getModifiedTime()));
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
