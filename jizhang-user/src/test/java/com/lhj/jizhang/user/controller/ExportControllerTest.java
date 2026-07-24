package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.service.ExportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportControllerTest {
    @Test
    void shouldReturnPlainAsciiExportFileName() {
        ExportService exportService = mock(ExportService.class);
        ExportService.ExportFile file = new ExportService.ExportFile(
                "bills-20260724-181530.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1});
        when(exportService.download(7L, 9L)).thenReturn(file);
        ExportController controller = new ExportController(exportService);

        ResponseEntity<byte[]> response = controller.download(new AuthenticatedUser(7L, 1), 9L);

        assertEquals("attachment; filename=\"bills-20260724-181530.xlsx\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }
}
