package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookDeletionService {
    private static final Logger log = LoggerFactory.getLogger(BookDeletionService.class);
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;
    private final Path backupRoot;

    /** 注入账本访问检查、数据库操作及本地文件存储目录。 */
    public BookDeletionService(BookMapper bookMapper, BookAccessService bookAccessService, JdbcTemplate jdbcTemplate,
                               @Value("${app.storage.upload-dir:./data/uploads}") String uploadDir,
                               @Value("${app.storage.backup-dir:./data/backups}") String backupDir) {
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.backupRoot = Path.of(backupDir).toAbsolutePath().normalize();
    }

    /** 永久删除所有者的账本、关联记录及本地文件。 */
    @Transactional
    public void delete(Long userId, Long bookId) {
        bookAccessService.requireOwner(userId, bookId);
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || !userId.equals(book.getOwnerUserId())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "仅账本所有者可删除账本", HttpStatus.FORBIDDEN);
        }
        jdbcTemplate.queryForObject("SELECT id FROM fin_book WHERE id = ? FOR UPDATE", Long.class, bookId);
        List<Path> files = collectFiles(bookId);
        deleteDependents(bookId);
        jdbcTemplate.update("DELETE FROM fin_book WHERE id = ?", bookId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFiles(bookId, files);
            }
        });
    }

    /** 收集属于该账本的附件、导出文件、备份文件和封面。 */
    private List<Path> collectFiles(Long bookId) {
        List<Path> files = new ArrayList<>();
        jdbcTemplate.queryForList("SELECT a.object_key FROM fin_attachment a JOIN fin_transaction t ON t.id = a.transaction_id WHERE t.book_id = ? AND a.storage_provider = 'LOCAL'", String.class, bookId)
                .forEach(key -> addFile(files, uploadRoot, key));
        jdbcTemplate.queryForList("SELECT file_object_key FROM sys_export_task WHERE book_id = ? AND file_object_key IS NOT NULL", String.class, bookId)
                .forEach(key -> addFile(files, uploadRoot, key));
        jdbcTemplate.queryForList("SELECT file_path FROM fin_backup_task WHERE book_id = ? AND file_path IS NOT NULL", String.class, bookId)
                .forEach(key -> addFile(files, backupRoot, key));
        Path coverDirectory = uploadRoot.resolve("book-covers").resolve(bookId.toString()).normalize();
        if (coverDirectory.startsWith(uploadRoot)) {
            try (var paths = Files.list(coverDirectory)) {
                paths.filter(Files::isRegularFile).forEach(files::add);
            } catch (java.nio.file.NoSuchFileException ignored) {
            } catch (IOException exception) {
                throw new IllegalStateException("读取账本封面失败", exception);
            }
        }
        return files;
    }

    /** 只允许清理配置存储目录下的文件。 */
    private void addFile(List<Path> files, Path root, String key) {
        if (key == null) {
            return;
        }
        Path path = root.resolve(key).normalize();
        if (path.startsWith(root) && !path.equals(root)) {
            files.add(path);
        }
    }

    /** 按外键依赖顺序删除账本关联记录，包含已软删除的历史数据。 */
    private void deleteDependents(Long bookId) {
        jdbcTemplate.update("DELETE r FROM fin_restore_check r JOIN fin_backup_task b ON b.id = r.backup_id WHERE b.book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_backup_task WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE c FROM fin_savings_contribution c LEFT JOIN fin_savings_goal g ON g.id = c.goal_id LEFT JOIN fin_transaction t ON t.id = c.transaction_id WHERE g.book_id = ? OR t.book_id = ?", bookId, bookId);
        jdbcTemplate.update("DELETE FROM fin_savings_goal WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_reimbursement WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_duplicate_finding WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE e FROM fin_recurring_execution e LEFT JOIN fin_recurring_rule r ON r.id = e.rule_id LEFT JOIN fin_transaction t ON t.id = e.transaction_id WHERE r.book_id = ? OR t.book_id = ?", bookId, bookId);
        jdbcTemplate.update("DELETE FROM fin_recurring_rule WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE a FROM fin_attachment a JOIN fin_transaction t ON t.id = a.transaction_id WHERE t.book_id = ?", bookId);
        jdbcTemplate.update("DELETE e FROM fin_account_entry e LEFT JOIN fin_transaction t ON t.id = e.transaction_id LEFT JOIN fin_account a ON a.id = e.account_id WHERE t.book_id = ? OR a.book_id = ?", bookId, bookId);
        jdbcTemplate.update("DELETE FROM fin_loan_record WHERE book_id = ?", bookId);
        jdbcTemplate.update("UPDATE fin_transaction SET original_transaction_id = NULL WHERE book_id = ? AND original_transaction_id IS NOT NULL", bookId);
        jdbcTemplate.update("DELETE FROM fin_transaction WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE i FROM fin_budget_item i JOIN fin_budget b ON b.id = i.budget_id WHERE b.book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_budget WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_account WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_category WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_daily_summary WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_monthly_summary WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM sys_export_task WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_book_audit_log WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_book_invitation WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM fin_book_member WHERE book_id = ?", bookId);
        jdbcTemplate.update("DELETE FROM sys_operation_log WHERE book_id = ?", bookId);
    }

    /** 数据库提交后清理文件，避免数据库回滚时文件已消失。 */
    private void deleteFiles(Long bookId, List<Path> files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                log.error("账本 {} 的文件清理失败: {}", bookId, file, exception);
            }
        }
        try {
            Files.deleteIfExists(uploadRoot.resolve("book-covers").resolve(bookId.toString()));
        } catch (IOException exception) {
            log.error("账本 {} 的封面目录清理失败", bookId, exception);
        }
    }
}
