package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.entity.AttachmentEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AttachmentMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.TransactionScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private BookAccessService bookAccessService;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentMapper, transactionMapper, bookAccessService, "./data/uploads");
    }

    /** 非本人账单的附件列表不读取附件并返回未找到。 */
    @Test
    void shouldHideOtherMembersAttachments() {
        when(transactionMapper.selectById(100L)).thenReturn(transaction(8L));
        when(bookAccessService.requireTransactionScope(7L, 1L)).thenReturn(TransactionScope.OWN_CREATED);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.list(7L, 100L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(attachmentMapper, never()).selectList(any(Wrapper.class));
    }

    /** 下载附件前校验所属账单的创建人。 */
    @Test
    void shouldHideOtherMembersAttachmentDownload() {
        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(200L);
        attachment.setTransactionId(100L);
        attachment.setStatus(1);
        when(attachmentMapper.selectById(200L)).thenReturn(attachment);
        when(transactionMapper.selectById(100L)).thenReturn(transaction(8L));
        when(bookAccessService.requireTransactionScope(7L, 1L)).thenReturn(TransactionScope.OWN_CREATED);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.download(7L, 200L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    /** 本人账单可以列出自身附件。 */
    @Test
    void shouldListOwnAttachments() {
        when(transactionMapper.selectById(100L)).thenReturn(transaction(7L));
        when(bookAccessService.requireTransactionScope(7L, 1L)).thenReturn(TransactionScope.OWN_CREATED);
        when(attachmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertEquals(List.of(), service.list(7L, 100L));
        verify(attachmentMapper).selectList(any(Wrapper.class));
    }

    /** 构造测试账单并指定创建人。 */
    private TransactionEntity transaction(Long creatorId) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(100L);
        transaction.setBookId(1L);
        transaction.setCreatedUserId(creatorId);
        return transaction;
    }
}
