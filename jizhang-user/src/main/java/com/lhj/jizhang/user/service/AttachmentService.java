package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.TransactionAttachmentOutDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.AttachmentEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AttachmentMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.AttachmentDownload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final AttachmentMapper attachmentMapper;
    private final TransactionMapper transactionMapper;
    private final BookAccessService bookAccessService;
    private final Path uploadRoot;

    public AttachmentService(AttachmentMapper attachmentMapper, TransactionMapper transactionMapper,
                             BookAccessService bookAccessService,
                             @Value("${app.storage.upload-dir:./data/uploads}") String uploadDir) {
        this.attachmentMapper = attachmentMapper;
        this.transactionMapper = transactionMapper;
        this.bookAccessService = bookAccessService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public TransactionAttachmentOutDTO upload(Long userId, Long transactionId, MultipartFile file) {
        TransactionEntity transaction = requireTransaction(transactionId);
        bookAccessService.requireWritable(userId, transaction.getBookId());
        validateFile(file);
        String objectKey = UUID.randomUUID() + IMAGE_EXTENSIONS.get(file.getContentType());
        Path target = uploadRoot.resolve(objectKey).normalize();
        try (InputStream inputStream = file.getInputStream()) {
            Files.createDirectories(uploadRoot);
            Files.copy(inputStream, target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "图片保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        AttachmentEntity attachment = buildAttachment(userId, transactionId, file, objectKey);
        attachmentMapper.insert(attachment);
        return toOutput(attachment);
    }

    public List<TransactionAttachmentOutDTO> list(Long userId, Long transactionId) {
        TransactionEntity transaction = requireTransaction(transactionId);
        bookAccessService.requireMember(userId, transaction.getBookId());
        return listEntities(transactionId).stream().map(this::toOutput).toList();
    }

    public TransactionOutDTO enrich(Long userId, TransactionOutDTO transaction) {
        List<TransactionAttachmentOutDTO> attachments = list(userId, transaction.id());
        return new TransactionOutDTO(transaction.id(), transaction.transactionNo(), transaction.requestId(),
                transaction.bookId(), transaction.createdUserId(), transaction.transactionType(),
                transaction.categoryId(), transaction.originalTransactionId(), transaction.amount(),
                transaction.currencyCode(), transaction.happenedAt(), transaction.title(), transaction.note(),
                transaction.status(), transaction.version(), transaction.entries(), attachments);
    }

    public AttachmentDownload download(Long userId, Long attachmentId) {
        AttachmentEntity attachment = requireAttachment(attachmentId);
        TransactionEntity transaction = requireTransaction(attachment.getTransactionId());
        bookAccessService.requireMember(userId, transaction.getBookId());
        Path file = resolveStoredFile(attachment);
        return new AttachmentDownload(new FileSystemResource(file), attachment.getFileName(),
                attachment.getContentType());
    }

    @Transactional
    public void delete(Long userId, Long attachmentId) {
        AttachmentEntity attachment = requireAttachment(attachmentId);
        TransactionEntity transaction = requireTransaction(attachment.getTransactionId());
        bookAccessService.requireWritable(userId, transaction.getBookId());
        attachment.setStatus(0);
        attachment.setModifier(String.valueOf(userId));
        attachmentMapper.updateById(attachment);
        try {
            Files.deleteIfExists(resolveStoredFile(attachment));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "图片删除失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "请选择图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "图片大小不能超过10MB");
        }
        if (!IMAGE_EXTENSIONS.containsKey(file.getContentType())) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "仅支持JPG、PNG和WebP图片");
        }
    }

    private AttachmentEntity buildAttachment(Long userId, Long transactionId, MultipartFile file, String objectKey) {
        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setTransactionId(transactionId);
        attachment.setStorageProvider("LOCAL");
        attachment.setObjectKey(objectKey);
        attachment.setFileName(safeFileName(file.getOriginalFilename()));
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setStatus(1);
        attachment.setCreator(String.valueOf(userId));
        attachment.setModifier(String.valueOf(userId));
        return attachment;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        String normalized = Path.of(fileName).getFileName().toString();
        return normalized.length() > 255 ? normalized.substring(normalized.length() - 255) : normalized;
    }

    private TransactionEntity requireTransaction(Long transactionId) {
        TransactionEntity transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        return transaction;
    }

    private AttachmentEntity requireAttachment(Long attachmentId) {
        AttachmentEntity attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || attachment.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "图片不存在");
        }
        return attachment;
    }

    private List<AttachmentEntity> listEntities(Long transactionId) {
        return attachmentMapper.selectList(Wrappers.<AttachmentEntity>lambdaQuery()
                .eq(AttachmentEntity::getTransactionId, transactionId)
                .eq(AttachmentEntity::getStatus, 1)
                .orderByAsc(AttachmentEntity::getId));
    }

    private Path resolveStoredFile(AttachmentEntity attachment) {
        Path file = uploadRoot.resolve(attachment.getObjectKey()).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCodes.ATTACHMENT_INVALID, "图片文件不存在");
        }
        return file;
    }

    private TransactionAttachmentOutDTO toOutput(AttachmentEntity attachment) {
        return new TransactionAttachmentOutDTO(attachment.getId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getFileSize(),
                "/api/v1/attachments/" + attachment.getId());
    }
}
