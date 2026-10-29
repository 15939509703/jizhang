package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.user.dto.DuplicateFindingOutDTO;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.DuplicateFindingEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BackupTaskMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.DuplicateFindingMapper;
import com.lhj.jizhang.user.mapper.RestoreCheckMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSecurityServiceTest {
    /** 验证两笔账单摘要分别对应原账单与待作废候选账单。 */
    @Test
    void findingsIncludeBothTransactionSummaries() {
        DuplicateFindingMapper findingMapper = mock(DuplicateFindingMapper.class);
        TransactionMapper transactionMapper = mock(TransactionMapper.class);
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        BookAccessService accessService = mock(BookAccessService.class);
        DuplicateFindingEntity finding = new DuplicateFindingEntity();
        finding.setId(1L);
        finding.setTransactionId(340L);
        finding.setCandidateTransactionId(341L);
        finding.setStatus("PENDING");
        TransactionEntity original = new TransactionEntity();
        original.setId(340L);
        original.setTitle("午餐");
        original.setTransactionType("EXPENSE");
        original.setAmount(new BigDecimal("28.00"));
        original.setCurrencyCode("CNY");
        original.setCategoryId(10L);
        original.setHappenedAt(LocalDateTime.of(2026, 7, 26, 3, 30));
        original.setStatus("EFFECTIVE");
        TransactionEntity candidate = new TransactionEntity();
        candidate.setId(341L);
        candidate.setTitle("工作餐");
        candidate.setTransactionType("EXPENSE");
        candidate.setAmount(new BigDecimal("28.00"));
        candidate.setCurrencyCode("CNY");
        candidate.setHappenedAt(LocalDateTime.of(2026, 7, 26, 3, 33));
        candidate.setNote("同事代付");
        candidate.setStatus("EFFECTIVE");
        CategoryEntity category = new CategoryEntity();
        category.setId(10L);
        category.setName("餐饮");
        when(findingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(finding));
        when(transactionMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(original, candidate));
        when(categoryMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(category));
        DataSecurityService service = new DataSecurityService(mock(BackupTaskMapper.class),
                mock(RestoreCheckMapper.class), findingMapper, mock(BookMapper.class), categoryMapper,
                mock(AccountMapper.class), transactionMapper, mock(AccountEntryMapper.class), accessService,
                mock(TransactionService.class), new ObjectMapper(), "./data/backups");

        DuplicateFindingOutDTO result = service.findings(7L, 1L).get(0);

        assertEquals("午餐", result.transaction().title());
        assertEquals("餐饮", result.transaction().categoryName());
        assertEquals("2026-07-26T03:30:00Z", result.transaction().happenedAt().toString());
        assertEquals("工作餐", result.candidateTransaction().title());
        assertEquals("同事代付", result.candidateTransaction().note());
        assertNull(result.candidateTransaction().categoryName());
        verify(accessService).requireMember(7L, 1L);
    }
}
