package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.ReimbursementCreateInDTO;
import com.lhj.jizhang.user.dto.ReimbursementOutDTO;
import com.lhj.jizhang.user.dto.ReimbursementReceiveInDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.ReimbursementEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.ReimbursementMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        TransactionEntity expense = transactionMapper.selectById(input.expenseTransactionId());
        if (expense == null || !"EXPENSE".equals(expense.getTransactionType())
                || !"EFFECTIVE".equals(expense.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "只有有效支出可以设为待报销");
        }
        bookAccessService.requireWritable(userId, expense.getBookId());
        ReimbursementEntity existing = reimbursementMapper.selectOne(Wrappers.<ReimbursementEntity>lambdaQuery()
                .eq(ReimbursementEntity::getExpenseTransactionId, expense.getId()));
        if (existing != null) return toOutput(existing);
        ReimbursementEntity entity = new ReimbursementEntity();
        entity.setReimbursementNo(BusinessIdGenerator.next("RMB_"));
        entity.setBookId(expense.getBookId());
        entity.setExpenseTransactionId(expense.getId());
        entity.setExpectedAmount(expense.getAmount());
        entity.setReimburserName(trim(input.reimburserName()));
        entity.setSubmittedDate(input.submittedDate());
        entity.setExpectedDate(input.expectedDate());
        entity.setStatus("PENDING");
        entity.setNote(trim(input.note()));
        entity.setVersion(0);
        entity.setCreator(String.valueOf(userId));
        entity.setModifier(String.valueOf(userId));
        try {
            reimbursementMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            return toOutput(reimbursementMapper.selectOne(Wrappers.<ReimbursementEntity>lambdaQuery()
                    .eq(ReimbursementEntity::getExpenseTransactionId, expense.getId())));
        }
        return toOutput(entity);
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
        if (entity == null) throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "报销项目不存在");
        bookAccessService.requireWritable(userId, entity.getBookId());
        return entity;
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
