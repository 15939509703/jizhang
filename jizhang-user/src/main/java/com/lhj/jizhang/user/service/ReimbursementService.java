package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.ReimbursementCreateInDTO;
import com.lhj.jizhang.user.dto.ReimbursementOutDTO;
import com.lhj.jizhang.user.dto.ReimbursementReceiveInDTO;
import com.lhj.jizhang.user.dto.ReimbursementUpdateInDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.ReimbursementEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.ReimbursementMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
public class ReimbursementService {
    private static final Set<String> STATUSES = Set.of("PENDING", "REIMBURSED", "CANCELLED");
    private final ReimbursementMapper reimbursementMapper;
    private final TransactionMapper transactionMapper;
    private final TransactionService transactionService;
    private final BookAccessService bookAccessService;

    public ReimbursementService(ReimbursementMapper reimbursementMapper, TransactionMapper transactionMapper,
                                TransactionService transactionService, BookAccessService bookAccessService) {
        this.reimbursementMapper = reimbursementMapper;
        this.transactionMapper = transactionMapper;
        this.transactionService = transactionService;
        this.bookAccessService = bookAccessService;
    }

    public List<ReimbursementOutDTO> list(Long userId, Long bookId, String status) {
        bookAccessService.requireMember(userId, bookId);
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "报销状态不正确");
        }
        var query = Wrappers.<ReimbursementEntity>lambdaQuery()
                .eq(ReimbursementEntity::getBookId, bookId)
                .orderByDesc(ReimbursementEntity::getId);
        if (status != null) query.eq(ReimbursementEntity::getStatus, status);
        return reimbursementMapper.selectList(query).stream().map(this::toOutput).toList();
    }

    @Transactional
    public ReimbursementOutDTO create(Long userId, ReimbursementCreateInDTO input) {
        if (input.expenseTransactionId() == null) {
            return createManual(userId, input);
        }
        if (input.bookId() != null || input.expectedAmount() != null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "关联账单和手动报销参数不能同时填写");
        }
        return createFromExpense(userId, input);
    }

    private ReimbursementOutDTO createFromExpense(Long userId, ReimbursementCreateInDTO input) {
        TransactionEntity expense = transactionMapper.selectById(input.expenseTransactionId());
        if (expense == null || !"EXPENSE".equals(expense.getTransactionType())
                || !"EFFECTIVE".equals(expense.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "只有有效支出可以设为待报销");
        }
        bookAccessService.requireWritable(userId, expense.getBookId());
        ReimbursementEntity existing = reimbursementMapper.selectOne(Wrappers.<ReimbursementEntity>lambdaQuery()
                .eq(ReimbursementEntity::getExpenseTransactionId, expense.getId()));
        if (existing != null) {
            return restoreOrReturn(userId, existing, input);
        }
        ReimbursementEntity entity = buildEntity(
                userId, input, expense.getBookId(), expense.getId(), expense.getAmount());
        try {
            reimbursementMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            return toOutput(reimbursementMapper.selectOne(Wrappers.<ReimbursementEntity>lambdaQuery()
                    .eq(ReimbursementEntity::getExpenseTransactionId, expense.getId())));
        }
        return toOutput(entity);
    }

    private ReimbursementOutDTO createManual(Long userId, ReimbursementCreateInDTO input) {
        validateManualInput(input.bookId(), input.expectedAmount());
        bookAccessService.requireWritable(userId, input.bookId());
        ReimbursementEntity entity = buildEntity(userId, input, input.bookId(), null, input.expectedAmount());
        reimbursementMapper.insert(entity);
        return toOutput(entity);
    }

    public ReimbursementOutDTO get(Long userId, Long id) {
        ReimbursementEntity entity = reimbursementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "报销项目不存在");
        }
        bookAccessService.requireMember(userId, entity.getBookId());
        return toOutput(entity);
    }

    @Transactional
    public ReimbursementOutDTO update(Long userId, Long id, ReimbursementUpdateInDTO input) {
        ReimbursementEntity entity = requireLocked(userId, id);
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "只有待报销项目可以编辑");
        }
        if (!input.version().equals(entity.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "报销项目已发生变化，请刷新后重试");
        }
        applyExpectedAmount(entity, input.expectedAmount());
        applyEditableFields(entity, input.reimburserName(), input.submittedDate(),
                input.expectedDate(), input.note());
        entity.setVersion(entity.getVersion() + 1);
        entity.setModifier(String.valueOf(userId));
        reimbursementMapper.updateById(entity);
        return toOutput(entity);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ReimbursementEntity entity = requireLocked(userId, id);
        if ("REIMBURSED".equals(entity.getStatus()) || entity.getReimbursementTransactionId() != null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "请先作废报销到账账单");
        }
        reimbursementMapper.deleteById(entity.getId());
    }

    @Transactional
    public ReimbursementOutDTO receive(Long userId, Long id, ReimbursementReceiveInDTO input) {
        ReimbursementEntity entity = requireLocked(userId, id);
        if ("REIMBURSED".equals(entity.getStatus())) return toOutput(entity);
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前报销项目不能确认到账");
        }
        TransactionOutDTO transaction = transactionService.create(userId, new TransactionCreateInDTO(
                "REIMBURSE_" + id + "_" + entity.getVersion(), entity.getBookId(), "INCOME", input.categoryId(),
                entity.getExpenseTransactionId(), input.accountId(), null, entity.getExpectedAmount(),
                input.happenedAt(), "报销到账", input.note()));
        entity.setReimbursementTransactionId(transaction.id());
        entity.setReimbursedTime(LocalDateTime.ofInstant(input.happenedAt(), ZoneOffset.UTC));
        entity.setStatus("REIMBURSED");
        entity.setVersion(entity.getVersion() + 1);
        entity.setModifier(String.valueOf(userId));
        reimbursementMapper.updateById(entity);
        return toOutput(entity);
    }

    @Transactional
    public ReimbursementOutDTO cancel(Long userId, Long id) {
        ReimbursementEntity entity = requireLocked(userId, id);
        if ("REIMBURSED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "请先作废报销到账账单");
        }
        if (!"CANCELLED".equals(entity.getStatus())) {
            entity.setStatus("CANCELLED");
            entity.setVersion(entity.getVersion() + 1);
            entity.setModifier(String.valueOf(userId));
            reimbursementMapper.updateById(entity);
        }
        return toOutput(entity);
    }

    private ReimbursementEntity requireLocked(Long userId, Long id) {
        ReimbursementEntity entity = reimbursementMapper.selectByIdForUpdate(id);
        if (entity == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "报销项目不存在");
        }
        bookAccessService.requireWritable(userId, entity.getBookId());
        return entity;
    }

    private ReimbursementOutDTO restoreOrReturn(Long userId, ReimbursementEntity entity,
                                                 ReimbursementCreateInDTO input) {
        if (!"CANCELLED".equals(entity.getStatus())) {
            return toOutput(entity);
        }
        applyEditableFields(entity, input.reimburserName(), input.submittedDate(),
                input.expectedDate(), input.note());
        entity.setStatus("PENDING");
        entity.setVersion(entity.getVersion() + 1);
        entity.setModifier(String.valueOf(userId));
        reimbursementMapper.updateById(entity);
        return toOutput(entity);
    }

    private ReimbursementEntity buildEntity(Long userId, ReimbursementCreateInDTO input,
                                             Long bookId, Long expenseTransactionId, BigDecimal expectedAmount) {
        ReimbursementEntity entity = new ReimbursementEntity();
        entity.setReimbursementNo(BusinessIdGenerator.next("RMB_"));
        entity.setBookId(bookId);
        entity.setExpenseTransactionId(expenseTransactionId);
        entity.setExpectedAmount(expectedAmount);
        applyEditableFields(entity, input.reimburserName(), input.submittedDate(),
                input.expectedDate(), input.note());
        entity.setStatus("PENDING");
        entity.setVersion(0);
        entity.setCreator(String.valueOf(userId));
        entity.setModifier(String.valueOf(userId));
        return entity;
    }

    private void applyExpectedAmount(ReimbursementEntity entity, BigDecimal expectedAmount) {
        if (entity.getExpenseTransactionId() != null) {
            if (expectedAmount != null && expectedAmount.compareTo(entity.getExpectedAmount()) != 0) {
                throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "关联账单的报销金额不能修改");
            }
            return;
        }
        validateManualInput(entity.getBookId(), expectedAmount);
        entity.setExpectedAmount(expectedAmount);
    }

    private void validateManualInput(Long bookId, BigDecimal expectedAmount) {
        if (bookId == null || expectedAmount == null || expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "手动报销必须填写账本和大于0的金额");
        }
    }

    private void applyEditableFields(ReimbursementEntity entity, String reimburserName,
                                     LocalDate submittedDate, LocalDate expectedDate,
                                     String note) {
        entity.setReimburserName(trim(reimburserName));
        entity.setSubmittedDate(submittedDate);
        entity.setExpectedDate(expectedDate);
        entity.setNote(trim(note));
    }

    private ReimbursementOutDTO toOutput(ReimbursementEntity entity) {
        return new ReimbursementOutDTO(entity.getId(), entity.getReimbursementNo(), entity.getBookId(),
                entity.getExpenseTransactionId(), entity.getReimbursementTransactionId(), entity.getExpectedAmount(),
                entity.getReimburserName(), entity.getSubmittedDate(), entity.getExpectedDate(),
                entity.getReimbursedTime() == null ? null : entity.getReimbursedTime().toInstant(ZoneOffset.UTC),
                entity.getStatus(), entity.getNote(), entity.getVersion());
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
