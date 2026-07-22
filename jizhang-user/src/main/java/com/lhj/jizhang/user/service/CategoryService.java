package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.CategoryCreateInDTO;
import com.lhj.jizhang.user.dto.CategoryOutDTO;
import com.lhj.jizhang.user.dto.CategorySortInDTO;
import com.lhj.jizhang.user.dto.CategoryUpdateInDTO;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<CategoryOutDTO> list(Long userId, Long bookId, String type, boolean includeHidden) {
        bookAccessService.requireMember(userId, bookId);
        validateType(type);
        var query = Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .eq(CategoryEntity::getDeletedFlag, 0)
                .orderByAsc(CategoryEntity::getCategoryType, CategoryEntity::getSortNo, CategoryEntity::getId);
        if (!includeHidden) {
            query.eq(CategoryEntity::getHiddenFlag, 0);
        }
        if (type != null) {
            query.eq(CategoryEntity::getCategoryType, type);
        }
        return categoryMapper.selectList(query).stream().map(this::toOutput).toList();
    }

    @Transactional
    public CategoryOutDTO create(Long userId, CategoryCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        CategoryEntity category = new CategoryEntity();
        category.setCategoryNo(BusinessIdGenerator.next("CAT_"));
        category.setScopeType("BOOK");
        category.setBookId(input.bookId());
        category.setCategoryType(input.categoryType());
        applyDisplay(category, input.name(), input.icon(), input.color());
        category.setSortNo(nextSortNo(input.bookId(), input.categoryType()));
        category.setSystemFlag(0);
        category.setHiddenFlag(0);
        category.setDeletedFlag(0);
        category.setCreator(String.valueOf(userId));
        category.setModifier(String.valueOf(userId));
        categoryMapper.insert(category);
        return toOutput(category);
    }

    @Transactional
    public CategoryOutDTO update(Long userId, Long categoryId, CategoryUpdateInDTO input) {
        CategoryEntity category = requireCategory(categoryId);
        bookAccessService.requireWritable(userId, category.getBookId());
        applyDisplay(category, input.name(), input.icon(), input.color());
        category.setModifier(String.valueOf(userId));
        categoryMapper.updateById(category);
        return toOutput(category);
    }

    @Transactional
    public CategoryOutDTO setHidden(Long userId, Long categoryId, boolean hidden) {
        CategoryEntity category = requireCategory(categoryId);
        bookAccessService.requireWritable(userId, category.getBookId());
        category.setHiddenFlag(hidden ? 1 : 0);
        category.setModifier(String.valueOf(userId));
        categoryMapper.updateById(category);
        return toOutput(category);
    }

    @Transactional
    public void sort(Long userId, CategorySortInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        for (var item : input.items()) {
            CategoryEntity category = requireCategory(item.categoryId());
            if (!input.bookId().equals(category.getBookId())) {
                throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类不属于当前账本");
            }
            category.setSortNo(item.sortNo());
            category.setModifier(String.valueOf(userId));
            categoryMapper.updateById(category);
        }
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        CategoryEntity category = requireCategory(categoryId);
        bookAccessService.requireWritable(userId, category.getBookId());
        category.setDeletedFlag(1);
        category.setModifier(String.valueOf(userId));
        categoryMapper.updateById(category);
    }

    private CategoryEntity requireCategory(Long categoryId) {
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null || category.getDeletedFlag() != 0 || !"BOOK".equals(category.getScopeType())) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类不存在或不可管理");
        }
        return category;
    }

    private void validateType(String type) {
        if (type != null && !CATEGORY_TYPES.contains(type)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "分类类型不正确");
        }
    }

    private int nextSortNo(Long bookId, String type) {
        CategoryEntity last = categoryMapper.selectOne(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId).eq(CategoryEntity::getCategoryType, type)
                .eq(CategoryEntity::getDeletedFlag, 0).orderByDesc(CategoryEntity::getSortNo).last("LIMIT 1"));
        return last == null ? 10 : last.getSortNo() + 10;
    }

    private void applyDisplay(CategoryEntity category, String name, String icon, String color) {
        category.setName(name.trim());
        category.setIcon(icon == null || icon.isBlank() ? "circle" : icon.trim());
        category.setColor(color == null || color.isBlank() ? "#64748B" : color.toUpperCase());
    }

    private CategoryOutDTO toOutput(CategoryEntity category) {
        return new CategoryOutDTO(category.getId(), category.getCategoryNo(), category.getCategoryType(),
                category.getParentId(), category.getName(), category.getIcon(), category.getColor(),
                category.getSortNo(), category.getHiddenFlag() == 1, category.getSystemFlag() == 1);
    }
}
