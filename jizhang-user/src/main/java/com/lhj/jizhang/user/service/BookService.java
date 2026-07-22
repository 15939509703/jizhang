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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookService {
    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final CategoryMapper categoryMapper;
    private final AccountMapper accountMapper;
    private final BookAccessService bookAccessService;

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
                .map(member -> toOutput(books.get(member.getBookId()), member.getRole()))
                .toList();
    }

    @Transactional
    public BookOutDTO create(Long userId, BookCreateInDTO input) {
        BookEntity book = createBook(userId, input.name(), input.description(),
                valueOrDefault(input.currencyCode(), "CNY"),
                valueOrDefault(input.timezone(), "Asia/Shanghai"));
        return toOutput(book, "OWNER");
    }

    @Transactional
    public BookEntity createDefault(Long userId) {
        return createBook(userId, "个人账本", "默认账本", "CNY", "Asia/Shanghai");
    }

    @Transactional
    public BookOutDTO update(Long userId, Long bookId, BookUpdateInDTO input) {
        BookMemberEntity member = bookAccessService.requireAdmin(userId, bookId);
        BookEntity book = requireBook(bookId);
        book.setName(input.name().trim());
        book.setDescription(blankToNull(input.description()));
        book.setCoverUrl(blankToNull(input.coverUrl()));
        book.setVersion(book.getVersion() + 1);
        book.setModifier(String.valueOf(userId));
        bookMapper.updateById(book);
        return toOutput(book, member.getRole());
    }

    private BookEntity createBook(Long userId, String name, String description, String currencyCode, String timezone) {
        validateTimezone(timezone);
        BookEntity book = buildBook(userId, name, description, currencyCode, timezone);
        bookMapper.insert(book);
        bookMemberMapper.insert(buildOwnerMembership(book.getId(), userId));
        copySystemCategories(book.getId(), userId);
        accountMapper.insert(buildDefaultAccount(book.getId(), userId));
        return book;
    }

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

    private void copySystemCategories(Long bookId, Long userId) {
        categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                        .eq(CategoryEntity::getScopeType, "SYSTEM")
                        .eq(CategoryEntity::getDeletedFlag, 0)
                        .orderByAsc(CategoryEntity::getCategoryType, CategoryEntity::getSortNo)).stream()
                .map(template -> copyCategory(template, bookId, userId))
                .forEach(categoryMapper::insert);
    }

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

    private BookOutDTO toOutput(BookEntity book, String role) {
        return new BookOutDTO(book.getId(), book.getBookNo(), book.getName(), book.getDescription(),
                book.getCoverUrl(), book.getCurrencyCode(), book.getTimezone(), role);
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void validateTimezone(String timezone) {
        try {
            java.time.ZoneId.of(timezone);
        } catch (java.time.DateTimeException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "时区格式不正确");
        }
    }
}
