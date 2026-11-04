package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserMapper;
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
public class UserAvatarService {
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private final UserMapper userMapper;
    private final Path uploadRoot;

    public UserAvatarService(UserMapper userMapper, @Value("${app.storage.upload-dir:./data/uploads}") String uploadDir) {
        this.userMapper = userMapper;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 保存当前用户头像并返回更新后的资料。 */
    public LoginUserOutDTO upload(Long userId, MultipartFile file) {
        UserEntity user = requireActiveUser(userId);
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "请选择不超过2MB的头像图片");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "头像读取失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (bytes.length == 0 || bytes.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "请选择不超过2MB的头像图片");
        }
        String extension = imageExtension(bytes);
        if (extension == null) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "仅支持JPG、PNG和WebP图片");
        }
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + extension;
        Path target = uploadRoot.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "头像保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String previousKey = user.getAvatarUrl();
        user.setAvatarUrl(key);
        user.setModifier(String.valueOf(userId));
        try {
            if (userMapper.updateById(user) != 1) {
                throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "头像更新失败");
            }
        } catch (RuntimeException exception) {
            user.setAvatarUrl(previousKey);
            deleteQuietly(target);
            throw exception;
        }
        if (isOwnedKey(userId, previousKey)) deleteQuietly(uploadRoot.resolve(previousKey));
        return new LoginUserOutDTO(user.getId(), user.getUserNo(), user.getNickName(), publicUrl(user));
    }

    /** 仅允许当前用户读取自己的本地头像。 */
    public AttachmentDownload download(Long userId) {
        String key = requireActiveUser(userId).getAvatarUrl();
        if (!isOwnedKey(userId, key)) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "头像不存在");
        }
        Path path = uploadRoot.resolve(key).normalize();
        if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "头像文件不存在");
        }
        String contentType = key.endsWith(".png") ? "image/png"
                : key.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new AttachmentDownload(new FileSystemResource(path), "avatar" + key.substring(key.lastIndexOf('.')),
                contentType);
    }

    /** 将本地存储键转换为需要登录态访问的头像地址，保留旧的微信头像地址。 */
    public static String publicUrl(UserEntity user) {
        String key = user.getAvatarUrl();
        return hasCustomAvatar(user)
                ? "/api/v1/users/me/avatar?v=" + key.substring(key.lastIndexOf('/') + 1) : key;
    }

    /** 判断用户是否已设置受保护的自定义头像。 */
    public static boolean hasCustomAvatar(UserEntity user) {
        return isOwnedKey(user.getId(), user.getAvatarUrl());
    }

    /** 校验登录用户仍处于启用状态。 */
    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请重新登录");
        }
        return user;
    }

    /** 限定头像文件必须属于当前用户的存储目录。 */
    private static boolean isOwnedKey(Long userId, String key) {
        return userId != null && key != null
                && key.matches("avatars/" + userId + "/[0-9a-f-]{36}\\.(jpg|png|webp)");
    }

    /** 根据图片文件头判断格式，不信任上传文件名和声明的类型。 */
    private String imageExtension(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return ".jpg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G' && (bytes[4] & 0xff) == 0x0d
                && (bytes[5] & 0xff) == 0x0a && (bytes[6] & 0xff) == 0x1a
                && (bytes[7] & 0xff) == 0x0a) return ".png";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return ".webp";
        return null;
    }

    /** 尽力清理被替换或写入失败的文件。 */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
