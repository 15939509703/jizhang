package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.BookMenu;

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

    /**
     * 初始化分类服务。
     *
     * @param categoryMapper 数据访问器
     * @param bookAccessService 关联服务
     */
    public CategoryService(CategoryMapper categoryMapper, BookAccessService bookAccessService) {
        this.categoryMapper = categoryMapper;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 查询分类列表。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param type 类型
     * @param includeHidden 是否启用
     * @return 分类列表
     */
    public List<CategoryOutDTO> list(Long userId, Long bookId, String type, boolean includeHidden) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.CATEGORIES);
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

    /**
     * 创建分类。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 分类输出对象
     */
    @Transactional
    public CategoryOutDTO create(Long userId, CategoryCreateInDTO input) {
        bookAccessService.requireAction(userId, input.bookId(), BookAction.CATEGORIES_MANAGE);
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

    /**
     * 更新分类。
     *
     * @param userId 用户ID
     * @param categoryId 分类ID
     * @param input 请求参数
     * @return 分类输出对象
     */
    @Transactional
    public CategoryOutDTO update(Long userId, Long categoryId, CategoryUpdateInDTO input) {
        CategoryEntity category = requireCategory(categoryId);
        bookAccessService.requireAction(userId, category.getBookId(), BookAction.CATEGORIES_MANAGE);
        applyDisplay(category, input.name(), input.icon(), input.color());
        category.setModifier(String.valueOf(userId));
        categoryMapper.updateById(category);
        return toOutput(category);
    }

    /**
     * 设置分类的隐藏状态。
     *
     * @param userId 用户ID
     * @param categoryId 分类ID
     * @param hidden 是否启用
     * @return 分类输出对象
     */
    @Transactional
    public CategoryOutDTO setHidden(Long userId, Long categoryId, boolean hidden) {
        CategoryEntity category = requireCategory(categoryId);
        bookAccessService.requireAction(userId, category.getBookId(), BookAction.CATEGORIES_MANAGE);
        category.setHiddenFlag(hidden ? 1 : 0);
        category.setModifier(String.valueOf(userId));
        categoryMapper.updateById(category);
        return toOutput(category);
    }

    /**
     * 按指定顺序重排分类。
     *
     * @param userId 用户ID
     * @param input 请求参数
     */
    @Transactional
    public void sort(Long userId, CategorySortInDTO input) {
        bookAccessService.requireAction(userId, input.bookId(), BookAction.CATEGORIES_MANAGE);
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

    /**
     * 删除分类。
     *
     * @param userId 用户ID
     * @param categoryId 分类ID
     */
    @Transactional
    public void delete(Long userId, Long categoryId) {
        CategoryEntity category = requireCategory(categoryId);
        bookAccessService.requireAction(userId, category.getBookId(), BookAction.CATEGORIES_MANAGE);
        category.setDeletedFlag(1);
        category.setModifier(String.valueOf(userId));
        categoryMapper.updateById(category);
    }

    /**
     * 查询并校验分类。
     *
     * @param categoryId 分类ID
     * @return 分类实体
     */
    private CategoryEntity requireCategory(Long categoryId) {
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null || category.getDeletedFlag() != 0 || !"BOOK".equals(category.getScopeType())) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类不存在或不可管理");
        }
        return category;
    }

    /**
     * 校验类型。
     *
     * @param type 类型
     */
    private void validateType(String type) {
        if (type != null && !CATEGORY_TYPES.contains(type)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "分类类型不正确");
        }
    }

    /**
     * 计算下一个分类排序号。
     *
     * @param bookId 账本ID
     * @param type 类型
     * @return 下一个分类排序号
     */
    private int nextSortNo(Long bookId, String type) {
        CategoryEntity last = categoryMapper.selectOne(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId).eq(CategoryEntity::getCategoryType, type)
                .eq(CategoryEntity::getDeletedFlag, 0).orderByDesc(CategoryEntity::getSortNo).last("LIMIT 1"));
        return last == null ? 10 : last.getSortNo() + 10;
    }

    /**
     * 更新分类的显示属性。
     *
     * @param category 分类实体
     * @param name 名称
     * @param icon 分类图标
     * @param color 显示颜色
     */
    private void applyDisplay(CategoryEntity category, String name, String icon, String color) {
        category.setName(name.trim());
        category.setIcon(icon == null || icon.isBlank() ? "circle" : icon.trim());
        category.setColor(color == null || color.isBlank() ? "#64748B" : color.toUpperCase());
    }

    /**
     * 转换为分类输出对象。
     *
     * @param category 分类实体
     * @return 分类输出对象
     */
    private CategoryOutDTO toOutput(CategoryEntity category) {
        return new CategoryOutDTO(category.getId(), category.getCategoryNo(), category.getCategoryType(),
                category.getParentId(), category.getName(), category.getIcon(), category.getColor(),
                category.getSortNo(), category.getHiddenFlag() == 1, category.getSystemFlag() == 1);
    }
}
