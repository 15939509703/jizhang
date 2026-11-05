package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookDeletionServiceTest {
    @TempDir
    Path temporaryDirectory;

    /** 管理员或其他成员不能触发任何数据库删除。 */
    @Test
    void rejectsNonOwnerBeforeDeleting() {
        BookMapper bookMapper = mock(BookMapper.class);
        BookAccessService accessService = mock(BookAccessService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BookDeletionService service = service(bookMapper, accessService, jdbcTemplate);
        doThrow(BusinessException.class).when(accessService).requireOwner(2L, 7L);

        assertThrows(BusinessException.class, () -> service.delete(2L, 7L));
        verifyNoInteractions(bookMapper, jdbcTemplate);
    }

    /** 成员角色异常时仍以账本所有者字段拒绝删除。 */
    @Test
    void rejectsMismatchedOwner() {
        BookMapper bookMapper = mock(BookMapper.class);
        BookAccessService accessService = mock(BookAccessService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BookEntity book = new BookEntity();
        book.setOwnerUserId(3L);
        when(bookMapper.selectById(7L)).thenReturn(book);

        assertThrows(BusinessException.class, () -> service(bookMapper, accessService, jdbcTemplate).delete(2L, 7L));
        verifyNoInteractions(jdbcTemplate);
    }

    /** 数据库先删除依赖记录，最后删除账本，文件清理延后到提交。 */
    @Test
    void deletesBookLastAndSchedulesFileCleanup() throws IOException {
        BookMapper bookMapper = mock(BookMapper.class);
        BookAccessService accessService = mock(BookAccessService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BookEntity book = new BookEntity();
        book.setOwnerUserId(2L);
        when(bookMapper.selectById(7L)).thenReturn(book);
        Path coverDirectory = temporaryDirectory.resolve("uploads/book-covers/7");
        Files.createDirectories(coverDirectory);
        Path cover = Files.createFile(coverDirectory.resolve("cover.png"));
        Path attachment = Files.createFile(temporaryDirectory.resolve("uploads/attachment.png"));
        Path backupDirectory = temporaryDirectory.resolve("backups");
        Files.createDirectories(backupDirectory);
        Path backup = Files.createFile(backupDirectory.resolve("backup.jzb"));
        when(jdbcTemplate.queryForList(startsWith("SELECT a.object_key"), eq(String.class), eq(7L)))
                .thenReturn(List.of("attachment.png"));
        when(jdbcTemplate.queryForList(startsWith("SELECT file_path"), eq(String.class), eq(7L)))
                .thenReturn(List.of("backup.jzb"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service(bookMapper, accessService, jdbcTemplate).delete(2L, 7L);
            assertTrue(Files.exists(cover));
            var order = inOrder(jdbcTemplate);
            order.verify(jdbcTemplate).update("DELETE FROM fin_backup_task WHERE book_id = ?", 7L);
            order.verify(jdbcTemplate).update("DELETE FROM fin_transaction WHERE book_id = ?", 7L);
            order.verify(jdbcTemplate).update("DELETE FROM fin_book WHERE id = ?", 7L);
            assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit()));
            assertFalse(Files.exists(cover));
            assertFalse(Files.exists(attachment));
            assertFalse(Files.exists(backup));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 构造隔离临时文件目录的删除服务。 */
    private BookDeletionService service(BookMapper bookMapper, BookAccessService accessService,
                                        JdbcTemplate jdbcTemplate) {
        return new BookDeletionService(bookMapper, accessService, jdbcTemplate,
                temporaryDirectory.resolve("uploads").toString(), temporaryDirectory.resolve("backups").toString());
    }
}
