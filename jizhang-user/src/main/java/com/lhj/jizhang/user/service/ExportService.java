package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.ExportCreateInDTO;
import com.lhj.jizhang.user.dto.ExportTaskOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.ExportTaskEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.ExportTaskMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {
    private static final Set<String> TRANSACTION_TYPES = Set.of("EXPENSE", "INCOME", "TRANSFER");
    private static final Set<String> STATUSES = Set.of("EFFECTIVE", "VOIDED", "REVERSED");

    private final ExportTaskMapper exportTaskMapper;
    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;
    private final AccountMapper accountMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final BookAccessService bookAccessService;
    private final ObjectMapper objectMapper;

    public ExportService(
            ExportTaskMapper exportTaskMapper,
            TransactionMapper transactionMapper,
            CategoryMapper categoryMapper,
            AccountMapper accountMapper,
            AccountEntryMapper accountEntryMapper,
            BookAccessService bookAccessService,
            ObjectMapper objectMapper
    ) {
        this.exportTaskMapper = exportTaskMapper;
        this.transactionMapper = transactionMapper;
        this.categoryMapper = categoryMapper;
        this.accountMapper = accountMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.bookAccessService = bookAccessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportTaskOutDTO create(Long userId, ExportCreateInDTO input) {
        bookAccessService.requireMember(userId, input.bookId());
        validateInput(input);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setTaskNo(BusinessIdGenerator.next("EXP_"));
        task.setUserId(userId);
        task.setBookId(input.bookId());
        task.setExportType(input.exportType());
        task.setQueryJson(writeQuery(input));
        task.setTaskStatus("SUCCESS");
        task.setStartedTime(LocalDateTime.now(ZoneOffset.UTC));
        task.setFinishedTime(LocalDateTime.now(ZoneOffset.UTC));
        task.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        task.setCreator(String.valueOf(userId));
        task.setModifier(String.valueOf(userId));
        exportTaskMapper.insert(task);
        task.setFileObjectKey("/api/v1/exports/" + task.getId() + "/download");
        exportTaskMapper.updateById(task);
        return toOutput(task);
    }

    public ExportTaskOutDTO get(Long userId, Long id) {
        ExportTaskEntity task = requireTask(userId, id);
        return toOutput(task);
    }

    public ExportFile download(Long userId, Long id) {
        ExportTaskEntity task = requireTask(userId, id);
        ExportCreateInDTO query = readQuery(task);
        List<TransactionEntity> rows = loadTransactions(query);
        ExportContext context = loadContext(query.bookId(), rows);
        String delimiter = "EXCEL".equals(task.getExportType()) ? "\t" : ",";
        String content = buildContent(rows, context, delimiter);
        String suffix = "EXCEL".equals(task.getExportType()) ? "xls" : "csv";
        String contentType = "EXCEL".equals(task.getExportType()) ? "application/vnd.ms-excel" : "text/csv";
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return new ExportFile("jizhang-" + task.getTaskNo() + "." + suffix, contentType, bytes);
    }

    private List<TransactionEntity> loadTransactions(ExportCreateInDTO input) {
        var query = Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, input.bookId())
                .orderByDesc(TransactionEntity::getHappenedAt, TransactionEntity::getId);
        if (input.type() != null && !input.type().isBlank()) {
            query.eq(TransactionEntity::getTransactionType, input.type());
        } else {
            query.in(TransactionEntity::getTransactionType, TRANSACTION_TYPES);
        }
        if (input.status() != null && !input.status().isBlank()) {
            query.eq(TransactionEntity::getStatus, input.status());
        }
        if (input.categoryId() != null) {
            query.eq(TransactionEntity::getCategoryId, input.categoryId());
        }
        if (input.accountId() != null) {
            query.inSql(TransactionEntity::getId,
                    "SELECT transaction_id FROM fin_account_entry WHERE account_id = " + input.accountId());
        }
        if (input.keyword() != null && !input.keyword().isBlank()) {
            String normalized = input.keyword().trim();
            query.and(wrapper -> wrapper.like(TransactionEntity::getTitle, normalized)
                    .or().like(TransactionEntity::getNote, normalized));
        }
        if (input.startAt() != null) {
            query.ge(TransactionEntity::getHappenedAt, toUtc(input.startAt()));
        }
        if (input.endAt() != null) {
            query.lt(TransactionEntity::getHappenedAt, toUtc(input.endAt()));
        }
        return transactionMapper.selectList(query);
    }

    private ExportContext loadContext(Long bookId, List<TransactionEntity> transactions) {
        Map<Long, CategoryEntity> categories = categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                        .eq(CategoryEntity::getBookId, bookId))
                .stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
        Map<Long, AccountEntity> accounts = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getBookId, bookId))
                .stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<Long, List<AccountEntryEntity>> entries = loadEntries(transactions);
        return new ExportContext(categories, accounts, entries);
    }

    private Map<Long, List<AccountEntryEntity>> loadEntries(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return Map.of();
        }
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                        .in(AccountEntryEntity::getTransactionId,
                                transactions.stream().map(TransactionEntity::getId).toList())
                        .orderByAsc(AccountEntryEntity::getId))
                .stream().collect(Collectors.groupingBy(AccountEntryEntity::getTransactionId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private String buildContent(List<TransactionEntity> rows, ExportContext context, String delimiter) {
        StringBuilder builder = new StringBuilder();
        addRow(builder, delimiter, List.of("发生时间", "类型", "标题", "分类", "账户", "金额", "状态", "备注"));
        for (TransactionEntity row : rows) {
            addRow(builder, delimiter, List.of(
                    String.valueOf(row.getHappenedAt()),
                    typeLabel(row.getTransactionType()),
                    row.getTitle(),
                    categoryName(context, row.getCategoryId()),
                    accountNames(context, row.getId()),
                    String.valueOf(row.getAmount()),
                    statusLabel(row.getStatus()),
                    row.getNote()
            ));
        }
        return builder.toString();
    }

    private void addRow(StringBuilder builder, String delimiter, List<String> values) {
        builder.append(values.stream().map(value -> escape(value, delimiter)).collect(Collectors.joining(delimiter)));
        builder.append('\n');
    }

    private String escape(String value, String delimiter) {
        String normalized = value == null ? "" : value;
        if ("\t".equals(delimiter)) {
            return normalized.replace('\t', ' ').replace('\n', ' ');
        }
        String escaped = normalized.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String categoryName(ExportContext context, Long categoryId) {
        CategoryEntity category = categoryId == null ? null : context.categories().get(categoryId);
        return category == null ? "" : category.getName();
    }

    private String accountNames(ExportContext context, Long transactionId) {
        return context.entries().getOrDefault(transactionId, List.of()).stream()
                .map(entry -> context.accounts().get(entry.getAccountId()))
                .filter(account -> account != null)
                .map(AccountEntity::getName)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    private void validateInput(ExportCreateInDTO input) {
        if (input.type() != null && !input.type().isBlank() && !TRANSACTION_TYPES.contains(input.type())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账单类型不正确");
        }
        if (input.status() != null && !input.status().isBlank() && !STATUSES.contains(input.status())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账单状态不正确");
        }
        if (input.startAt() != null && input.endAt() != null && !input.startAt().isBefore(input.endAt())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出开始时间必须早于结束时间");
        }
    }

    private ExportTaskEntity requireTask(Long userId, Long id) {
        ExportTaskEntity task = exportTaskMapper.selectById(id);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出任务不存在");
        }
        bookAccessService.requireMember(userId, task.getBookId());
        return task;
    }

    private String writeQuery(ExportCreateInDTO input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出条件格式不正确");
        }
    }

    private ExportCreateInDTO readQuery(ExportTaskEntity task) {
        try {
            return objectMapper.readValue(task.getQueryJson(), ExportCreateInDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出条件格式不正确");
        }
    }

    private ExportTaskOutDTO toOutput(ExportTaskEntity task) {
        return new ExportTaskOutDTO(task.getId(), task.getTaskNo(), task.getBookId(), task.getExportType(),
                task.getTaskStatus(), task.getFileObjectKey(), task.getFailureReason(), toInstant(task.getFinishedTime()),
                toInstant(task.getExpiredTime()));
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "EXPENSE" -> "支出";
            case "INCOME" -> "收入";
            case "TRANSFER" -> "转账";
            default -> type;
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "EFFECTIVE" -> "有效";
            case "VOIDED" -> "已作废";
            case "REVERSED" -> "已冲正";
            default -> status;
        };
    }

    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

    public record ExportFile(String fileName, String contentType, byte[] bytes) {
    }

    private record ExportContext(
            Map<Long, CategoryEntity> categories,
            Map<Long, AccountEntity> accounts,
            Map<Long, List<AccountEntryEntity>> entries
    ) {
    }
}
