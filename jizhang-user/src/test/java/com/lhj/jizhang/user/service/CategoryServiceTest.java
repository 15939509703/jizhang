package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.CategoryCreateInDTO;
import com.lhj.jizhang.user.dto.CategoryOutDTO;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {
    @Test
    void shouldCreateVisibleCustomCategoryWithNextSortNumber() {
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        CategoryEntity last = new CategoryEntity();
        last.setSortNo(20);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(last);
        doAnswer(invocation -> {
            invocation.<CategoryEntity>getArgument(0).setId(30L);
            return 1;
        }).when(categoryMapper).insert(any(CategoryEntity.class));
        CategoryService service = new CategoryService(categoryMapper, bookAccessService);

        CategoryOutDTO result = service.create(7L,
                new CategoryCreateInDTO(1L, "EXPENSE", "宠物", "宠", "#2587A8"));

        assertEquals(30L, result.id());
        assertEquals(30, result.sortNo());
        assertFalse(result.hidden());
        assertFalse(result.system());
        verify(bookAccessService).requireWritable(7L, 1L);
    }
}
