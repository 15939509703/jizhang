package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.BackupTaskOutDTO;
import com.lhj.jizhang.user.dto.DuplicateFindingOutDTO;
import com.lhj.jizhang.user.dto.RestoreCheckOutDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.BackupTaskEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.DuplicateFindingEntity;
import com.lhj.jizhang.user.entity.RestoreCheckEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BackupTaskMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.DuplicateFindingMapper;
import com.lhj.jizhang.user.mapper.RestoreCheckMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.BookPermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DataSecurityService {
    private static final int FORMAT_VERSION = 1;
    private final BackupTaskMapper backupMapper;
    private final RestoreCheckMapper restoreMapper;
    private final DuplicateFindingMapper findingMapper;
    private final BookMapper bookMapper;
    private final CategoryMapper categoryMapper;
    private final AccountMapper accountMapper;
    private final TransactionMapper transactionMapper;
    private final AccountEntryMapper entryMapper;
    private final BookAccessService accessService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final Path backupRoot;

    public DataSecurityService(BackupTaskMapper backupMapper, RestoreCheckMapper restoreMapper,
                               DuplicateFindingMapper findingMapper, BookMapper bookMapper,
                               CategoryMapper categoryMapper, AccountMapper accountMapper,
                               TransactionMapper transactionMapper, AccountEntryMapper entryMapper,
                               BookAccessService accessService, TransactionService transactionService,
                               ObjectMapper objectMapper,
                               @Value("${app.storage.backup-dir:./data/backups}") String backupDir) {
        this.backupMapper = backupMapper; this.restoreMapper = restoreMapper; this.findingMapper = findingMapper;
        this.bookMapper = bookMapper; this.categoryMapper = categoryMapper; this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper; this.entryMapper = entryMapper; this.accessService = accessService;
        this.transactionService = transactionService; this.objectMapper = objectMapper;
        this.backupRoot = Path.of(backupDir).toAbsolutePath().normalize();
    }

    public List<BackupTaskOutDTO> backups(Long userId, Long bookId) {
        accessService.requirePermission(userId, bookId, BookPermission.EXPORT_DATA);
        return backupMapper.selectList(Wrappers.<BackupTaskEntity>lambdaQuery().eq(BackupTaskEntity::getUserId, userId)
                .eq(BackupTaskEntity::getBookId, bookId).orderByDesc(BackupTaskEntity::getId).last("LIMIT 30"))
                .stream().map(this::backupOutput).toList();
    }

    @Transactional
    public BackupTaskOutDTO createBackup(Long userId, Long bookId) {
        accessService.requirePermission(userId, bookId, BookPermission.EXPORT_DATA);
        BackupTaskEntity task = new BackupTaskEntity();
        task.setTaskNo(BusinessIdGenerator.next("BAK_")); task.setUserId(userId); task.setBookId(bookId);
        task.setFormatVersion(FORMAT_VERSION); task.setTaskStatus("PROCESSING");
        task.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        task.setCreator(String.valueOf(userId)); task.setModifier(String.valueOf(userId)); backupMapper.insert(task);
        try {
            byte[] encrypted = encrypt(objectMapper.writeValueAsBytes(payload(bookId)));
            Files.createDirectories(backupRoot);
            String fileName = task.getTaskNo() + ".jzb";
            Files.write(backupRoot.resolve(fileName), encrypted, StandardOpenOption.CREATE_NEW);
            task.setFilePath(fileName); task.setChecksum(sha256(encrypted)); task.setTaskStatus("SUCCESS");
        } catch (Exception exception) {
            task.setTaskStatus("FAILED"); task.setFailureCode("BACKUP_WRITE_FAILED");
        }
        backupMapper.updateById(task);
        return backupOutput(task);
    }

    public BackupFile download(Long userId, Long id) {
        BackupTaskEntity task = requireBackup(userId, id);
        try {
            byte[] bytes = Files.readAllBytes(resolveFile(task));
            if (!sha256(bytes).equals(task.getChecksum())) throw new IOException("checksum");
            return new BackupFile(task.getTaskNo() + ".jzb", bytes);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "备份文件不存在或已损坏", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public RestoreCheckOutDTO checkRestore(Long userId, Long id) {
        BackupTaskEntity task = requireBackup(userId, id);
        BackupPayload payload = readPayload(task);
        accessService.requirePermission(userId, payload.book().getId(), BookPermission.CREATE_TRANSACTIONS);
        int skipped = 0; int added = 0; int conflicts = 0;
        for (BackupTransaction item : payload.transactions) {
            TransactionEntity existing = transactionMapper.selectOne(Wrappers.<TransactionEntity>lambdaQuery()
                    .eq(TransactionEntity::getBookId, payload.book().getId())
                    .and(query -> query.eq(TransactionEntity::getTransactionNo, item.transaction().getTransactionNo())
                            .or().eq(TransactionEntity::getRequestId, restoreRequestId(item.transaction()))));
            if (existing == null) added++; else if (same(existing, item.transaction())) skipped++; else conflicts++;
        }
        RestoreCheckEntity check = new RestoreCheckEntity();
        check.setBackupId(id); check.setUserId(userId); check.setAddedCount(added); check.setSkippedCount(skipped);
        check.setConflictCount(conflicts); check.setStatus(conflicts == 0 ? "PASSED" : "CONFLICT");
        check.setReportJson("{\"formatVersion\":" + payload.formatVersion + "}");
        check.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        check.setCreator(String.valueOf(userId)); check.setModifier(String.valueOf(userId)); restoreMapper.insert(check);
        return restoreOutput(check);
    }

    @Transactional
    public RestoreCheckOutDTO restore(Long userId, Long checkId) {
        RestoreCheckEntity check = restoreMapper.selectById(checkId);
        if (check == null || !userId.equals(check.getUserId()) || !"PASSED".equals(check.getStatus())
                || check.getExpiredTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "恢复检查不存在、未通过或已过期");
        }
        BackupTaskEntity task = requireBackup(userId, check.getBackupId());
        BackupPayload payload = readPayload(task);
        accessService.requirePermission(userId, payload.book().getId(), BookPermission.CREATE_TRANSACTIONS);
        int restored = 0;
        for (BackupTransaction item : payload.transactions()) {
            TransactionEntity source = item.transaction();
            if (!"EFFECTIVE".equals(source.getStatus())) continue;
            TransactionEntity existing = transactionMapper.selectOne(Wrappers.<TransactionEntity>lambdaQuery()
                    .eq(TransactionEntity::getBookId, payload.book().getId())
                    .eq(TransactionEntity::getRequestId, restoreRequestId(source)));
            if (existing != null) continue;
            Long accountId = entryAccount(item.entries(), "DECREASE");
            Long targetAccountId = entryAccount(item.entries(), "INCREASE");
            if ("INCOME".equals(source.getTransactionType())) accountId = targetAccountId;
            transactionService.create(userId, new TransactionCreateInDTO(restoreRequestId(source), source.getBookId(),
                    source.getTransactionType(), source.getCategoryId(), null, accountId,
                    "TRANSFER".equals(source.getTransactionType()) ? targetAccountId : null, source.getAmount(),
                    source.getHappenedAt().toInstant(ZoneOffset.UTC), source.getTitle(), source.getNote()));
            restored++;
        }
        check.setAddedCount(restored);
        check.setStatus("RESTORED");
        check.setModifier(String.valueOf(userId));
        restoreMapper.updateById(check);
        return restoreOutput(check);
    }

    public List<DuplicateFindingOutDTO> findings(Long userId, Long bookId) {
        accessService.requireMember(userId, bookId);
        return findingMapper.selectList(Wrappers.<DuplicateFindingEntity>lambdaQuery()
                .eq(DuplicateFindingEntity::getBookId, bookId).orderByDesc(DuplicateFindingEntity::getId).last("LIMIT 100"))
                .stream().map(this::findingOutput).toList();
    }

    @Transactional
    public List<DuplicateFindingOutDTO> scanDuplicates(Long userId, Long bookId) {
        accessService.requirePermission(userId, bookId, BookPermission.EDIT_ALL_TRANSACTIONS);
        List<TransactionEntity> rows = transactionMapper.selectList(Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId).eq(TransactionEntity::getStatus, "EFFECTIVE")
                .orderByDesc(TransactionEntity::getHappenedAt).last("LIMIT 500"));
        for (int i = 0; i < rows.size(); i++) {
            for (int j = i + 1; j < rows.size(); j++) {
                TransactionEntity left = rows.get(i), right = rows.get(j);
                if (Duration.between(right.getHappenedAt(), left.getHappenedAt()).abs().toMinutes() > 10) break;
                if (!left.getTransactionType().equals(right.getTransactionType()) || left.getAmount().compareTo(right.getAmount()) != 0) continue;
                int score = 70;
                if (java.util.Objects.equals(left.getCategoryId(), right.getCategoryId())) score += 10;
                if (normalize(left.getTitle()).equals(normalize(right.getTitle()))) score += 20;
                if (score < 80) continue;
                DuplicateFindingEntity finding = new DuplicateFindingEntity();
                finding.setBookId(bookId); finding.setTransactionId(Math.min(left.getId(), right.getId()));
                finding.setCandidateTransactionId(Math.max(left.getId(), right.getId()));
                finding.setSimilarity(BigDecimal.valueOf(score, 2));
                finding.setReason("同类型、同金额且发生时间相近" + (score == 100 ? "，标题和分类一致" : ""));
                finding.setStatus("PENDING"); finding.setCreator(String.valueOf(userId)); finding.setModifier(String.valueOf(userId));
                try { findingMapper.insert(finding); } catch (DuplicateKeyException ignored) { }
            }
        }
        return findings(userId, bookId);
    }

    @Transactional
    public DuplicateFindingOutDTO handleFinding(Long userId, Long id, boolean confirmDuplicate) {
        DuplicateFindingEntity finding = findingMapper.selectById(id);
        if (finding == null) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "重复候选不存在");
        accessService.requirePermission(userId, finding.getBookId(), BookPermission.EDIT_ALL_TRANSACTIONS);
        if ("PENDING".equals(finding.getStatus()) && confirmDuplicate) {
            transactionService.voidTransaction(userId, finding.getCandidateTransactionId());
            finding.setStatus("CONFIRMED");
        } else if ("PENDING".equals(finding.getStatus())) finding.setStatus("IGNORED");
        finding.setHandledUserId(userId); finding.setModifier(String.valueOf(userId)); findingMapper.updateById(finding);
        return findingOutput(finding);
    }

    private BackupPayload payload(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        List<CategoryEntity> categories = categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getBookId, bookId));
        List<AccountEntity> accounts = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery().eq(AccountEntity::getBookId, bookId));
        List<TransactionEntity> transactions = transactionMapper.selectList(Wrappers.<TransactionEntity>lambdaQuery().eq(TransactionEntity::getBookId, bookId));
        List<BackupTransaction> items = new ArrayList<>();
        for (TransactionEntity transaction : transactions) {
            List<AccountEntryEntity> entries = entryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                    .eq(AccountEntryEntity::getTransactionId, transaction.getId()).ne(AccountEntryEntity::getEntryType, "REVERSAL"));
            items.add(new BackupTransaction(transaction, entries));
        }
        return new BackupPayload(FORMAT_VERSION, book, categories, accounts, items);
    }

    private BackupPayload readPayload(BackupTaskEntity task) {
        try {
            byte[] file = Files.readAllBytes(resolveFile(task));
            if (!sha256(file).equals(task.getChecksum())) throw new IOException("checksum");
            BackupPayload payload = objectMapper.readValue(decrypt(file), BackupPayload.class);
            if (payload.formatVersion != FORMAT_VERSION) throw new IOException("version");
            return payload;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "备份损坏或版本不兼容");
        }
    }

    private byte[] encrypt(byte[] plain) throws Exception {
        byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, backupKey(), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plain); byte[] output = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, output, 0, nonce.length); System.arraycopy(encrypted, 0, output, nonce.length, encrypted.length);
        return output;
    }

    private byte[] decrypt(byte[] input) throws Exception {
        if (input.length < 28) throw new IOException("short");
        byte[] nonce = java.util.Arrays.copyOfRange(input, 0, 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, backupKey(), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(input, 12, input.length - 12);
    }

    private SecretKey backupKey() throws Exception {
        Files.createDirectories(backupRoot);
        Path keyPath = backupRoot.resolve(".backup.key");
        if (!Files.exists(keyPath)) {
            KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
            try { Files.write(keyPath, generator.generateKey().getEncoded(), StandardOpenOption.CREATE_NEW); }
            catch (java.nio.file.FileAlreadyExistsException ignored) { }
        }
        return new SecretKeySpec(Files.readAllBytes(keyPath), "AES");
    }

    private BackupTaskEntity requireBackup(Long userId, Long id) {
        BackupTaskEntity task = backupMapper.selectById(id);
        if (task == null || !userId.equals(task.getUserId()) || !"SUCCESS".equals(task.getTaskStatus()))
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "备份任务不存在或不可用");
        accessService.requirePermission(userId, task.getBookId(), BookPermission.EXPORT_DATA);
        return task;
    }

    private Path resolveFile(BackupTaskEntity task) {
        Path path = backupRoot.resolve(task.getFilePath()).normalize();
        if (!path.startsWith(backupRoot)) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "备份路径无效");
        return path;
    }

    private boolean same(TransactionEntity a, TransactionEntity b) {
        return a.getTransactionType().equals(b.getTransactionType()) && a.getAmount().compareTo(b.getAmount()) == 0;
    }
    private String restoreRequestId(TransactionEntity item) { return "RESTORE_" + item.getTransactionNo(); }
    private Long entryAccount(List<AccountEntryEntity> entries, String type) {
        return entries.stream().filter(entry -> type.equals(entry.getEntryType())).map(AccountEntryEntity::getAccountId)
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCodes.INVALID_PARAMETER, "备份账单分录不完整"));
    }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private BackupTaskOutDTO backupOutput(BackupTaskEntity item) { return new BackupTaskOutDTO(item.getId(), item.getTaskNo(), item.getBookId(), item.getTaskStatus(), item.getChecksum(), item.getFormatVersion(), item.getCreatedTime() == null ? null : item.getCreatedTime().toInstant(ZoneOffset.UTC), item.getExpiredTime() == null ? null : item.getExpiredTime().toInstant(ZoneOffset.UTC)); }
    private RestoreCheckOutDTO restoreOutput(RestoreCheckEntity item) { return new RestoreCheckOutDTO(item.getId(), item.getBackupId(), item.getAddedCount(), item.getSkippedCount(), item.getConflictCount(), item.getStatus()); }
    private DuplicateFindingOutDTO findingOutput(DuplicateFindingEntity item) { return new DuplicateFindingOutDTO(item.getId(), item.getTransactionId(), item.getCandidateTransactionId(), item.getSimilarity(), item.getReason(), item.getStatus()); }

    public record BackupFile(String fileName, byte[] bytes) { }
    private record BackupPayload(int formatVersion, BookEntity book, List<CategoryEntity> categories,
                                 List<AccountEntity> accounts, List<BackupTransaction> transactions) { }
    private record BackupTransaction(TransactionEntity transaction, List<AccountEntryEntity> entries) { }
}
