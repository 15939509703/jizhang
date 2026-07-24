package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.user.entity.ExportTaskEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.ExportTaskMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportServiceTest {
    @Test
    void shouldDownloadStructuredExcelWithStyledHeaderAndEmptyNote() throws IOException {
        ExportTaskMapper exportTaskMapper = mock(ExportTaskMapper.class);
        TransactionMapper transactionMapper = mock(TransactionMapper.class);
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        AccountMapper accountMapper = mock(AccountMapper.class);
        AccountEntryMapper accountEntryMapper = mock(AccountEntryMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        when(exportTaskMapper.selectById(9L)).thenReturn(exportTask());
        when(transactionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(transactionWithoutNote()));
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(accountEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        ExportService service = new ExportService(exportTaskMapper, transactionMapper, categoryMapper,
                accountMapper, accountEntryMapper, bookAccessService, new ObjectMapper().findAndRegisterModules());

        ExportService.ExportFile file = service.download(7L, 9L);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.contentType());
        assertEquals("bills-20260724-181530.xlsx", file.fileName());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.bytes()))) {
            Sheet sheet = workbook.getSheet("账单明细");
            assertEquals(8, sheet.getRow(0).getLastCellNum());
            assertEquals("发生时间", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("备注", sheet.getRow(0).getCell(7).getStringCellValue());
            assertEquals(IndexedColors.YELLOW.getIndex(), sheet.getRow(0).getCell(0).getCellStyle()
                    .getFillForegroundColor());
            assertEquals("停车费", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("10.00", sheet.getRow(1).getCell(5).getStringCellValue());
            assertEquals("", sheet.getRow(1).getCell(7).getStringCellValue());
        }
        verify(bookAccessService).requireMember(7L, 1L);
    }

    private ExportTaskEntity exportTask() {
        ExportTaskEntity task = new ExportTaskEntity();
        task.setId(9L);
        task.setTaskNo("EXP_TEST");
        task.setUserId(7L);
        task.setBookId(1L);
        task.setExportType("EXCEL");
        task.setQueryJson("{\"bookId\":1,\"exportType\":\"EXCEL\",\"status\":\"EFFECTIVE\"}");
        task.setFinishedTime(LocalDateTime.of(2026, 7, 24, 10, 15, 30));
        return task;
    }

    private TransactionEntity transactionWithoutNote() {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(20L);
        transaction.setBookId(1L);
        transaction.setTransactionType("EXPENSE");
        transaction.setTitle("停车费");
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setStatus("EFFECTIVE");
        transaction.setNote(null);
        transaction.setHappenedAt(LocalDateTime.of(2026, 7, 24, 3, 19));
        return transaction;
    }
}
