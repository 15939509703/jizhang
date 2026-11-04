package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.model.AttachmentDownload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class BookCoverService {
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;
    private final BookService bookService;
    private final Path uploadRoot;

    public BookCoverService(BookMapper bookMapper, BookAccessService bookAccessService, BookService bookService,
                            @Value("${app.storage.upload-dir:./data/uploads}") String uploadDir) {
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
        this.bookService = bookService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 管理员上传账本封面，替换成功后清理旧文件。 */
    public BookOutDTO upload(Long userId, Long bookId, MultipartFile file) {
        BookMemberEntity member = bookAccessService.requireAdmin(userId, bookId);
        BookEntity book = bookService.requireBook(bookId);
        byte[] bytes = imageBytes(file);
        String extension = UserAvatarService.imageExtension(bytes);
        if (extension == null) throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "仅支持JPG、PNG和WebP图片");
        String key = "book-covers/" + bookId + "/" + UUID.randomUUID() + extension;
        Path target = uploadRoot.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "封面保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String previousKey = book.getCoverUrl();
        book.setCoverUrl(key);
        book.setVersion(book.getVersion() + 1);
        book.setModifier(String.valueOf(userId));
        try {
            if (bookMapper.updateById(book) != 1) throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "封面更新失败");
        } catch (RuntimeException exception) {
            book.setCoverUrl(previousKey);
            deleteQuietly(target);
            throw exception;
        }
        if (isOwnedKey(bookId, previousKey)) deleteQuietly(uploadRoot.resolve(previousKey));
        return bookService.toOutput(book, member.getRole());
    }

    /** 仅允许账本成员下载本地存储的封面。 */
    public AttachmentDownload download(Long userId, Long bookId) {
        bookAccessService.requireMember(userId, bookId);
        String key = bookService.requireBook(bookId).getCoverUrl();
        if (!isOwnedKey(bookId, key)) throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "封面不存在");
        Path path = uploadRoot.resolve(key).normalize();
        if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "封面文件不存在");
        }
        String contentType = key.endsWith(".png") ? "image/png" : key.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new AttachmentDownload(new FileSystemResource(path), "cover" + key.substring(key.lastIndexOf('.')),
                contentType);
    }

    /** 将本地封面存储键转换为需要登录态访问的地址。 */
    static String publicUrl(BookEntity book) {
        String key = book.getCoverUrl();
        return isOwnedKey(book.getId(), key)
                ? "/api/v1/books/" + book.getId() + "/cover?v=" + key.substring(key.lastIndexOf('/') + 1) : key;
    }

    /** 读取并校验图片体积。 */
    private byte[] imageBytes(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "请选择不超过2MB的封面图片");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > 0 && bytes.length <= MAX_FILE_SIZE) return bytes;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "封面读取失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "请选择不超过2MB的封面图片");
    }

    /** 限定封面文件属于当前账本的存储目录。 */
    private static boolean isOwnedKey(Long bookId, String key) {
        return bookId != null && key != null
                && key.matches("book-covers/" + bookId + "/[0-9a-f-]{36}\\.(jpg|png|webp)");
    }

    /** 尽力清理替换或写入失败的图片。 */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
