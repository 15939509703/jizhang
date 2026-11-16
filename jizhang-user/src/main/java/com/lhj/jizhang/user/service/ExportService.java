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
import com.lhj.jizhang.user.model.BookPermission;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {
    private static final List<String> EXPORT_HEADERS = List.of(
            "发生时间", "类型", "标题", "分类", "账户", "金额", "状态", "备注");
    private static final int[] EXCEL_COLUMN_WIDTHS = {22, 10, 24, 16, 24, 14, 10, 30};
    private static final DateTimeFormatter EXPORT_FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId EXPORT_FILE_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Set<String> TRANSACTION_TYPES = Set.of("EXPENSE", "INCOME", "TRANSFER");
    private static final Set<String> STATUSES = Set.of("EFFECTIVE", "VOIDED", "REVERSED");

    private final ExportTaskMapper exportTaskMapper;
    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;
    private final AccountMapper accountMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final BookAccessService bookAccessService;
    private final ObjectMapper objectMapper;

    /**
     * 初始化导出任务服务。
     *
     * @param exportTaskMapper 数据访问器
     * @param transactionMapper 数据访问器
     * @param categoryMapper 数据访问器
     * @param accountMapper 数据访问器
     * @param accountEntryMapper 数据访问器
     * @param bookAccessService 关联服务
     * @param objectMapper 数据访问器
     */
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

    /**
     * 创建导出任务。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 导出任务输出对象
     */
    @Transactional
    public ExportTaskOutDTO create(Long userId, ExportCreateInDTO input) {
        bookAccessService.requireLedgerFileAccess(userId, input.bookId());
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

    /**
     * 查询导出任务。
     *
     * @param userId 用户ID
     * @param id 记录ID
     * @return 导出任务输出对象
     */
    public ExportTaskOutDTO get(Long userId, Long id) {
        ExportTaskEntity task = requireTask(userId, id);
        return toOutput(task);
    }

    /**
     * 下载已生成的导出文件。
     *
     * @param userId 用户ID
     * @param id 记录ID
     * @return 导出文件
     */
    public ExportFile download(Long userId, Long id) {
        ExportTaskEntity task = requireTask(userId, id);
        ExportCreateInDTO query = readQuery(task);
        List<TransactionEntity> rows = loadTransactions(query);
        ExportContext context = loadContext(query.bookId(), rows);
        if ("EXCEL".equals(task.getExportType())) {
            return buildExcelFile(task, rows, context);
        }
        return buildCsvFile(task, rows, context);
    }

    /**
     * 查询符合导出条件的账单。
     *
     * @param input 请求参数
     * @return 导出任务列表
     */
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

    /**
     * 批量加载导出所需的关联数据。
     *
     * @param bookId 账本ID
     * @param transactions 账单集合
     * @return 导出关联数据
     */
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

    /**
     * 加载流水。
     *
     * @param transactions 账单集合
     * @return 导出任务映射
     */
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

    /**
     * 封装生成的 Excel 导出文件。
     *
     * @param task 任务实体
     * @param rows 数据行列表
     * @param context 关联上下文
     * @return 导出文件
     */
    private ExportFile buildExcelFile(ExportTaskEntity task, List<TransactionEntity> rows, ExportContext context) {
        String fileName = exportFileName(task, "xlsx");
        return new ExportFile(fileName, XLSX_CONTENT_TYPE, buildExcel(rows, context));
    }

    /**
     * 生成 Excel 文件字节。
     *
     * @param rows 数据行列表
     * @param context 关联上下文
     * @return 文件字节
     */
    private byte[] buildExcel(List<TransactionEntity> rows, ExportContext context) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("账单明细");
            populateExcelSheet(workbook, sheet, rows, context);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "导出文件生成失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 将账单数据写入 Excel 工作表。
     *
     * @param workbook Excel 工作簿
     * @param sheet Excel 工作表
     * @param rows 数据行列表
     * @param context 关联上下文
     */
    private void populateExcelSheet(Workbook workbook, Sheet sheet, List<TransactionEntity> rows,
                                    ExportContext context) {
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        addExcelRow(headerRow, EXPORT_HEADERS, createHeaderStyle(workbook));
        for (int index = 0; index < rows.size(); index++) {
            addExcelRow(sheet.createRow(index + 1), transactionValues(rows.get(index), context), null);
        }
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < EXCEL_COLUMN_WIDTHS.length; index++) {
            sheet.setColumnWidth(index, EXCEL_COLUMN_WIDTHS[index] * 256);
        }
    }

    /**
     * 创建 Excel 表头样式。
     *
     * @param workbook Excel 工作簿
     * @return Excel 表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * 将一组字段写入 Excel 行。
     *
     * @param row 当前数据行
     * @param values 字段值列表
     * @param style 单元格样式
     */
    private void addExcelRow(Row row, List<String> values, CellStyle style) {
        for (int index = 0; index < values.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values.get(index) == null ? "" : values.get(index));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    /**
     * 封装生成的 CSV 导出文件。
     *
     * @param task 任务实体
     * @param rows 数据行列表
     * @param context 关联上下文
     * @return 导出文件
     */
    private ExportFile buildCsvFile(ExportTaskEntity task, List<TransactionEntity> rows, ExportContext context) {
        String content = buildDelimitedContent(rows, context, ",");
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return new ExportFile(exportFileName(task, "csv"), "text/csv", bytes);
    }

    /**
     * 生成导出文件名。
     *
     * @param task 任务实体
     * @param suffix 文件扩展名
     * @return 处理后的文本
     */
    private String exportFileName(ExportTaskEntity task, String suffix) {
        LocalDateTime finishedTime = task.getFinishedTime() == null
                ? LocalDateTime.now(ZoneOffset.UTC) : task.getFinishedTime();
        String timestamp = finishedTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(EXPORT_FILE_TIME_ZONE)
                .format(EXPORT_FILE_TIME_FORMAT);
        return "bills-" + timestamp + "." + suffix;
    }

    /**
     * 生成 CSV 等分隔格式的导出内容。
     *
     * @param rows 数据行列表
     * @param context 关联上下文
     * @param delimiter 字段分隔符
     * @return 处理后的文本
     */
    private String buildDelimitedContent(List<TransactionEntity> rows, ExportContext context, String delimiter) {
        StringBuilder builder = new StringBuilder();
        addRow(builder, delimiter, EXPORT_HEADERS);
        for (TransactionEntity row : rows) {
            addRow(builder, delimiter, transactionValues(row, context));
        }
        return builder.toString();
    }

    /**
     * 提取账单导出字段。
     *
     * @param row 当前数据行
     * @param context 关联上下文
     * @return 导出任务列表
     */
    private List<String> transactionValues(TransactionEntity row, ExportContext context) {
        return Arrays.asList(String.valueOf(row.getHappenedAt()), typeLabel(row.getTransactionType()),
                row.getTitle(), categoryName(context, row.getCategoryId()), accountNames(context, row.getId()),
                String.valueOf(row.getAmount()), statusLabel(row.getStatus()), row.getNote());
    }

    /**
     * 追加一行分隔格式文本。
     *
     * @param builder 内容构建器
     * @param delimiter 字段分隔符
     * @param values 字段值列表
     */
    private void addRow(StringBuilder builder, String delimiter, List<String> values) {
        builder.append(values.stream().map(value -> escape(value, delimiter)).collect(Collectors.joining(delimiter)));
        builder.append('\n');
    }

    /**
     * 转义分隔文本中的特殊字符。
     *
     * @param value 原始值
     * @param delimiter 字段分隔符
     * @return 处理后的文本
     */
    private String escape(String value, String delimiter) {
        String normalized = value == null ? "" : value;
        if ("\t".equals(delimiter)) {
            return normalized.replace('\t', ' ').replace('\n', ' ');
        }
        String escaped = normalized.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    /**
     * 读取导出账单的分类名称。
     *
     * @param context 关联上下文
     * @param categoryId 分类ID
     * @return 处理后的文本
     */
    private String categoryName(ExportContext context, Long categoryId) {
        CategoryEntity category = categoryId == null ? null : context.categories().get(categoryId);
        return category == null ? "" : category.getName();
    }

    /**
     * 读取导出账单关联的账户名称。
     *
     * @param context 关联上下文
     * @param transactionId 账单ID
     * @return 处理后的文本
     */
    private String accountNames(ExportContext context, Long transactionId) {
        return context.entries().getOrDefault(transactionId, List.of()).stream()
                .map(entry -> context.accounts().get(entry.getAccountId()))
                .filter(account -> account != null)
                .map(AccountEntity::getName)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    /**
     * 校验输入。
     *
     * @param input 请求参数
     */
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

    /**
     * 查询并校验用户的导出任务。
     *
     * @param userId 用户ID
     * @param id 记录ID
     * @return 导出任务实体
     */
    private ExportTaskEntity requireTask(Long userId, Long id) {
        ExportTaskEntity task = exportTaskMapper.selectById(id);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出任务不存在");
        }
        bookAccessService.requireLedgerFileAccess(userId, task.getBookId());
        return task;
    }

    /**
     * 序列化导出查询条件。
     *
     * @param input 请求参数
     * @return 处理后的文本
     */
    private String writeQuery(ExportCreateInDTO input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出条件格式不正确");
        }
    }

    /**
     * 反序列化导出查询条件。
     *
     * @param task 任务实体
     * @return 导出任务请求参数
     */
    private ExportCreateInDTO readQuery(ExportTaskEntity task) {
        try {
            return objectMapper.readValue(task.getQueryJson(), ExportCreateInDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "导出条件格式不正确");
        }
    }

    /**
     * 转换为导出任务输出对象。
     *
     * @param task 任务实体
     * @return 导出任务输出对象
     */
    private ExportTaskOutDTO toOutput(ExportTaskEntity task) {
        return new ExportTaskOutDTO(task.getId(), task.getTaskNo(), task.getBookId(), task.getExportType(),
                task.getTaskStatus(), task.getFileObjectKey(), task.getFailureReason(), toInstant(task.getFinishedTime()),
                toInstant(task.getExpiredTime()));
    }

    /**
     * 转换账单类型的显示名称。
     *
     * @param type 类型
     * @return 处理后的文本
     */
    private String typeLabel(String type) {
        return switch (type) {
            case "EXPENSE" -> "支出";
            case "INCOME" -> "收入";
            case "TRANSFER" -> "转账";
            default -> type;
        };
    }

    /**
     * 转换账单状态的显示名称。
     *
     * @param status 状态
     * @return 处理后的文本
     */
    private String statusLabel(String status) {
        return switch (status) {
            case "EFFECTIVE" -> "有效";
            case "VOIDED" -> "已作废";
            case "REVERSED" -> "已冲正";
            default -> status;
        };
    }

    /**
     * 将时间转换为 UTC 时间。
     *
     * @param instant 待转换时间戳
     * @return 转换后的时间
     */
    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * 将数据库时间转换为时间戳。
     *
     * @param time 待转换时间
     * @return 转换后的时间
     */
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
