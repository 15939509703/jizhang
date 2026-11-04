package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.BookUpdateInDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookCoverServiceTest {
    @TempDir
    Path uploadRoot;

    /** 上传新封面后仅成员可读取，并清理原有封面。 */
    @Test
    void shouldReplaceCoverAndRestrictDownload() throws IOException {
        BookMapper mapper = mock(BookMapper.class);
        BookAccessService access = mock(BookAccessService.class);
        BookService books = mock(BookService.class);
        BookEntity book = book();
        book.setCoverUrl("book-covers/7/00000000-0000-0000-0000-000000000001.png");
        Path previous = uploadRoot.resolve(book.getCoverUrl());
        Files.createDirectories(previous.getParent());
        Files.write(previous, new byte[]{1});
        BookMemberEntity member = new BookMemberEntity();
        member.setRole("OWNER");
        when(access.requireAdmin(3L, 7L)).thenReturn(member);
        when(books.requireBook(7L)).thenReturn(book);
        when(mapper.updateById(book)).thenReturn(1);
        BookCoverService service = new BookCoverService(mapper, access, books, uploadRoot.toString());

        service.upload(3L, 7L, pngFile());

        assertTrue(BookCoverService.publicUrl(book).startsWith("/api/v1/books/7/cover?v="));
        assertTrue(Files.exists(uploadRoot.resolve(book.getCoverUrl())));
        assertFalse(Files.exists(previous));
        assertEquals("image/png", service.download(3L, 7L).contentType());
        verify(access).requireMember(3L, 7L);
        verify(mapper).updateById(book);
    }

    /** 非图片与超限文件不能写入账本。 */
    @Test
    void shouldRejectInvalidImages() {
        BookMapper mapper = mock(BookMapper.class);
        BookAccessService access = mock(BookAccessService.class);
        BookService books = mock(BookService.class);
        when(books.requireBook(7L)).thenReturn(book());
        BookCoverService service = new BookCoverService(mapper, access, books, uploadRoot.toString());

        assertThrows(BusinessException.class, () -> service.upload(3L, 7L,
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        assertThrows(BusinessException.class, () -> service.upload(3L, 7L,
                new MockMultipartFile("file", "big.png", "image/png", new byte[2 * 1024 * 1024 + 1])));
        verify(mapper, never()).updateById(any(BookEntity.class));
    }

    /** 权限检查发生在文件读取和写入之前。 */
    @Test
    void shouldRejectUploadWithoutAdminAccess() {
        BookAccessService access = mock(BookAccessService.class);
        when(access.requireAdmin(3L, 7L)).thenThrow(new BusinessException("BOOK_ACCESS_DENIED", "无权访问"));
        BookService books = mock(BookService.class);
        BookCoverService service = new BookCoverService(mock(BookMapper.class), access, books, uploadRoot.toString());

        assertThrows(BusinessException.class, () -> service.upload(3L, 7L, pngFile()));
        verify(books, never()).requireBook(7L);
    }

    /** 数据库写入失败时不留下新图片，也不改变原封面。 */
    @Test
    void shouldRemoveNewCoverWhenUpdateFails() throws IOException {
        BookMapper mapper = mock(BookMapper.class);
        BookAccessService access = mock(BookAccessService.class);
        BookService books = mock(BookService.class);
        BookEntity book = book();
        book.setCoverUrl("https://example.com/old.png");
        when(books.requireBook(7L)).thenReturn(book);
        BookCoverService service = new BookCoverService(mapper, access, books, uploadRoot.toString());

        assertThrows(BusinessException.class, () -> service.upload(3L, 7L, pngFile()));

        assertEquals("https://example.com/old.png", book.getCoverUrl());
        try (var files = Files.list(uploadRoot.resolve("book-covers/7"))) {
            assertEquals(0, files.count());
        }
    }

    /** 修改名称时，已上传封面的公开地址仍映射原存储键。 */
    @Test
    void shouldPreserveUploadedCoverWhenEditingName() {
        BookMapper mapper = mock(BookMapper.class);
        BookAccessService access = mock(BookAccessService.class);
        BookMemberEntity member = new BookMemberEntity();
        member.setRole("OWNER");
        BookEntity book = book();
        book.setCoverUrl("book-covers/7/00000000-0000-0000-0000-000000000001.png");
        when(access.requireAdmin(3L, 7L)).thenReturn(member);
        when(mapper.selectById(7L)).thenReturn(book);
        BookService service = new BookService(mapper, mock(BookMemberMapper.class), mock(CategoryMapper.class),
                mock(AccountMapper.class), access);

        var result = service.update(3L, 7L, new BookUpdateInDTO("新名称", "说明", BookCoverService.publicUrl(book)));

        assertEquals("book-covers/7/00000000-0000-0000-0000-000000000001.png", book.getCoverUrl());
        assertEquals("/api/v1/books/7/cover?v=00000000-0000-0000-0000-000000000001.png", result.coverUrl());
    }

    /** 构造可用账本。 */
    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setId(7L);
        book.setName("账本");
        book.setVersion(0);
        book.setStatus(1);
        return book;
    }

    /** 构造带图片文件头的测试文件。 */
    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "cover.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
    }
}
