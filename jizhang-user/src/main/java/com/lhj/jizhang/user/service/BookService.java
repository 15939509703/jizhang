package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.BookCreateInDTO;
import com.lhj.jizhang.user.dto.BookOutDTO;
import com.lhj.jizhang.user.dto.BookUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookService {
    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final CategoryMapper categoryMapper;
    private final AccountMapper accountMapper;
    private final BookAccessService bookAccessService;

    /**
     * 初始化账本服务。
     *
     * @param bookMapper 数据访问器
     * @param bookMemberMapper 数据访问器
     * @param categoryMapper 数据访问器
     * @param accountMapper 数据访问器
     * @param bookAccessService 关联服务
     */
    public BookService(
            BookMapper bookMapper,
            BookMemberMapper bookMemberMapper,
            CategoryMapper categoryMapper,
            AccountMapper accountMapper,
            BookAccessService bookAccessService
    ) {
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
        this.categoryMapper = categoryMapper;
        this.accountMapper = accountMapper;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 查询账本列表。
     *
     * @param userId 用户ID
     * @return 账本列表
     */
    public List<BookOutDTO> list(Long userId) {
        List<BookMemberEntity> memberships = bookMemberMapper.selectList(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getUserId, userId)
                .eq(BookMemberEntity::getStatus, 1)
                .orderByAsc(BookMemberEntity::getId));
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, BookEntity> books = bookMapper.selectByIds(memberships.stream()
                        .map(BookMemberEntity::getBookId).toList()).stream()
                .filter(book -> book.getStatus() != null && book.getStatus() != 0)
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));
        return memberships.stream()
                .filter(member -> books.containsKey(member.getBookId()))
                .map(member -> toOutput(books.get(member.getBookId()), member.getRole(), member.getRemarkName()))
                .toList();
    }

    /**
     * 创建账本。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 账本输出对象
     */
    @Transactional
    public BookOutDTO create(Long userId, BookCreateInDTO input) {
        BookEntity book = createBook(userId, input.name(), input.description(),
                valueOrDefault(input.currencyCode(), "CNY"),
                valueOrDefault(input.timezone(), "Asia/Shanghai"));
        return toOutput(book, "OWNER", null);
    }

    /**
     * 创建用户默认账本。
     *
     * @param userId 用户ID
     * @return 账本实体
     */
    @Transactional
    public BookEntity createDefault(Long userId) {
        return createBook(userId, "个人账本", "默认账本", "CNY", "Asia/Shanghai");
    }

    /**
     * 更新账本。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param input 请求参数
     * @return 账本输出对象
     */
    @Transactional
    public BookOutDTO update(Long userId, Long bookId, BookUpdateInDTO input) {
        BookMemberEntity member = bookAccessService.requireAdmin(userId, bookId);
        BookEntity book = requireBook(bookId);
        book.setName(input.name().trim());
        book.setDescription(blankToNull(input.description()));
        String coverUrl = blankToNull(input.coverUrl());
        if (!Objects.equals(BookCoverService.publicUrl(book), coverUrl)) book.setCoverUrl(coverUrl);
        book.setVersion(book.getVersion() + 1);
        book.setModifier(String.valueOf(userId));
        bookMapper.updateById(book);
        return toOutput(book, member.getRole(), member.getRemarkName());
    }

    /** 仅更新当前登录用户在该账本的私有备注，不修改账本及所属人资料。 */
    @Transactional
    public BookOutDTO updateRemark(Long userId, Long bookId, String remarkName) {
        BookMemberEntity member = bookAccessService.requireMember(userId, bookId);
        BookEntity book = requireBook(bookId);
        String normalizedRemark = blankToNull(remarkName);
        bookMemberMapper.updateRemark(member.getId(), normalizedRemark, String.valueOf(userId));
        return toOutput(book, member.getRole(), normalizedRemark);
    }

    /**
     * 创建账本。
     *
     * @param userId 用户ID
     * @param name 名称
     * @param description 说明
     * @param currencyCode 币种代码
     * @param timezone 账本时区
     * @return 账本实体
     */
    private BookEntity createBook(Long userId, String name, String description, String currencyCode, String timezone) {
        validateTimezone(timezone);
        BookEntity book = buildBook(userId, name, description, currencyCode, timezone);
        bookMapper.insert(book);
        bookMemberMapper.insert(buildOwnerMembership(book.getId(), userId));
        copySystemCategories(book.getId(), userId);
        accountMapper.insert(buildDefaultAccount(book.getId(), userId));
        return book;
    }

    /**
     * 构建账本。
     *
     * @param userId 用户ID
     * @param name 名称
     * @param description 说明
     * @param currencyCode 币种代码
     * @param timezone 账本时区
     * @return 账本实体
     */
    private BookEntity buildBook(Long userId, String name, String description, String currencyCode, String timezone) {
        BookEntity book = new BookEntity();
        book.setBookNo(BusinessIdGenerator.next("BOOK_"));
        book.setOwnerUserId(userId);
        book.setName(name.trim());
        book.setDescription(description);
        book.setCurrencyCode(currencyCode);
        book.setTimezone(timezone);
        book.setStatus(1);
        book.setVersion(0);
        book.setCreator(String.valueOf(userId));
        book.setModifier(String.valueOf(userId));
        return book;
    }

    /**
     * 构建账本所有人的成员关系。
     *
     * @param bookId 账本ID
     * @param userId 用户ID
     * @return 账本成员实体
     */
    private BookMemberEntity buildOwnerMembership(Long bookId, Long userId) {
        BookMemberEntity member = new BookMemberEntity();
        member.setBookId(bookId);
        member.setUserId(userId);
        member.setRole("OWNER");
        member.setStatus(1);
        member.setJoinedTime(LocalDateTime.now(ZoneOffset.UTC));
        member.setCreator(String.valueOf(userId));
        member.setModifier(String.valueOf(userId));
        return member;
    }

    /**
     * 将系统分类复制到账本。
     *
     * @param bookId 账本ID
     * @param userId 用户ID
     */
    private void copySystemCategories(Long bookId, Long userId) {
        categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                        .eq(CategoryEntity::getScopeType, "SYSTEM")
                        .eq(CategoryEntity::getDeletedFlag, 0)
                        .orderByAsc(CategoryEntity::getCategoryType, CategoryEntity::getSortNo)).stream()
                .map(template -> copyCategory(template, bookId, userId))
                .forEach(categoryMapper::insert);
    }

    /**
     * 复制分类。
     *
     * @param template 模板数据
     * @param bookId 账本ID
     * @param userId 用户ID
     * @return 账本实体
     */
    private CategoryEntity copyCategory(CategoryEntity template, Long bookId, Long userId) {
        CategoryEntity category = new CategoryEntity();
        category.setCategoryNo(BusinessIdGenerator.next("CAT_"));
        category.setScopeType("BOOK");
        category.setBookId(bookId);
        category.setCategoryType(template.getCategoryType());
        category.setName(template.getName());
        category.setIcon(template.getIcon());
        category.setColor(template.getColor());
        category.setSortNo(template.getSortNo());
        category.setSystemFlag(0);
        category.setHiddenFlag(0);
        category.setDeletedFlag(0);
        category.setCreator(String.valueOf(userId));
        category.setModifier(String.valueOf(userId));
        return category;
    }

    /**
     * 构建默认账户。
     *
     * @param bookId 账本ID
     * @param userId 用户ID
     * @return 账本实体
     */
    private AccountEntity buildDefaultAccount(Long bookId, Long userId) {
        AccountEntity account = new AccountEntity();
        account.setAccountNo(BusinessIdGenerator.next("ACC_"));
        account.setBookId(bookId);
        account.setName("现金账户");
        account.setAccountType("CASH");
        account.setAccountNature("ASSET");
        account.setInitialBalance(BigDecimal.ZERO);
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setIncludedInAssets(1);
        account.setSortNo(10);
        account.setStatus(1);
        account.setVersion(0);
        account.setDeletedFlag(0);
        account.setCreator(String.valueOf(userId));
        account.setModifier(String.valueOf(userId));
        return account;
    }

    /**
     * 转换为账本输出对象。
     *
     * @param book 账本实体
     * @param role 成员角色
     * @param remarkName 账本私有备注
     * @return 账本输出对象
     */
    BookOutDTO toOutput(BookEntity book, String role, String remarkName) {
        return new BookOutDTO(book.getId(), book.getBookNo(), book.getName(), book.getDescription(),
                BookCoverService.publicUrl(book), book.getCurrencyCode(), book.getTimezone(), role, remarkName);
    }

    /**
     * 查询并校验账本存在。
     *
     * @param bookId 账本ID
     * @return 账本实体
     */
    BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    /**
     * 将空白文本转换为空值。
     *
     * @param value 原始值
     * @return 处理后的文本
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 为缺失值设置默认值。
     *
     * @param value 原始值
     * @param fallback 默认值
     * @return 处理后的文本
     */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 校验时区。
     *
     * @param timezone 账本时区
     */
    private void validateTimezone(String timezone) {
        try {
            java.time.ZoneId.of(timezone);
        } catch (java.time.DateTimeException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "时区格式不正确");
        }
    }
}
