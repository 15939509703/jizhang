package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.CategoryOutDTO;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class CategoryService {
    private static final Set<String> CATEGORY_TYPES = Set.of("EXPENSE", "INCOME");
    private final CategoryMapper categoryMapper;
    private final BookAccessService bookAccessService;

    public CategoryService(CategoryMapper categoryMapper, BookAccessService bookAccessService) {
        this.categoryMapper = categoryMapper;
        this.bookAccessService = bookAccessService;
    }

    public List<CategoryOutDTO> list(Long userId, Long bookId, String type) {
        bookAccessService.requireMember(userId, bookId);
        if (type != null && !CATEGORY_TYPES.contains(type)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "分类类型不正确");
        }
        var query = Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .eq(CategoryEntity::getHiddenFlag, 0)
                .eq(CategoryEntity::getDeletedFlag, 0)
                .orderByAsc(CategoryEntity::getCategoryType, CategoryEntity::getSortNo, CategoryEntity::getId);
        if (type != null) {
            query.eq(CategoryEntity::getCategoryType, type);
        }
        return categoryMapper.selectList(query).stream().map(this::toOutput).toList();
    }

    private CategoryOutDTO toOutput(CategoryEntity category) {
        return new CategoryOutDTO(category.getId(), category.getCategoryNo(), category.getCategoryType(),
                category.getParentId(), category.getName(), category.getIcon(), category.getColor(),
                category.getSortNo());
    }
}
