package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarServiceTest {
    @TempDir
    Path uploadRoot;

    /** 验证替换后旧文件被清理，新文件仅能通过当前用户接口访问。 */
    @Test
    void shouldReplaceAvatarAndKeepItPrivate() throws IOException {
        UserMapper mapper = mock(UserMapper.class);
        UserEntity user = activeUser();
        user.setAvatarUrl("avatars/7/00000000-0000-0000-0000-000000000001.png");
        Path previous = uploadRoot.resolve(user.getAvatarUrl());
        Files.createDirectories(previous.getParent());
        Files.write(previous, new byte[]{1});
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.updateById(user)).thenReturn(1);
        UserAvatarService service = new UserAvatarService(mapper, uploadRoot.toString());

        var profile = service.upload(7L, pngFile());

        assertTrue(profile.avatarUrl().startsWith("/api/v1/users/me/avatar?v="));
        assertTrue(user.getAvatarUrl().startsWith("avatars/7/"));
        assertTrue(Files.exists(uploadRoot.resolve(user.getAvatarUrl())));
        assertFalse(Files.exists(previous));
        assertEquals("image/png", service.download(7L).contentType());
        verify(mapper).updateById(user);
    }

    /** 非图片和超大文件不能改变头像。 */
    @Test
    void shouldRejectInvalidImageWithoutChangingProfile() {
        UserMapper mapper = mock(UserMapper.class);
        UserEntity user = activeUser();
        when(mapper.selectById(7L)).thenReturn(user);
        UserAvatarService service = new UserAvatarService(mapper, uploadRoot.toString());

        assertThrows(BusinessException.class, () -> service.upload(7L,
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        assertThrows(BusinessException.class, () -> service.upload(7L,
                new MockMultipartFile("file", "big.png", "image/png", new byte[2 * 1024 * 1024 + 1])));
        verify(mapper, never()).updateById(user);
    }

    /** 其他用户的存储键不能通过当前用户接口读取。 */
    @Test
    void shouldNotReadAnotherUsersAvatar() {
        UserMapper mapper = mock(UserMapper.class);
        UserEntity user = activeUser();
        user.setAvatarUrl("avatars/8/00000000-0000-0000-0000-000000000001.png");
        when(mapper.selectById(7L)).thenReturn(user);
        UserAvatarService service = new UserAvatarService(mapper, uploadRoot.toString());

        assertThrows(BusinessException.class, () -> service.download(7L));
    }

    /** 数据库写入失败时不保留新文件，也不改变原头像。 */
    @Test
    void shouldRemoveNewFileWhenUpdateFails() throws IOException {
        UserMapper mapper = mock(UserMapper.class);
        UserEntity user = activeUser();
        user.setAvatarUrl("https://example.com/old.png");
        when(mapper.selectById(7L)).thenReturn(user);
        UserAvatarService service = new UserAvatarService(mapper, uploadRoot.toString());

        assertThrows(BusinessException.class, () -> service.upload(7L, pngFile()));

        assertEquals("https://example.com/old.png", user.getAvatarUrl());
        try (var files = Files.list(uploadRoot.resolve("avatars/7"))) {
            assertEquals(0, files.count());
        }
    }

    /** 创建启用状态的测试用户。 */
    private UserEntity activeUser() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUserNo("USR_7");
        user.setNickName("用户");
        user.setStatus(1);
        return user;
    }

    /** 构造带 PNG 文件头的测试图片。 */
    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
    }
}
