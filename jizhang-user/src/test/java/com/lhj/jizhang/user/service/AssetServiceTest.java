package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.AssetSummaryOutDTO;
import com.lhj.jizhang.user.dto.AssetTrendOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private AccountEntryMapper accountEntryMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookAccessService bookAccessService;

    private AssetService service;

    @BeforeEach
    void setUp() {
        service = new AssetService(accountMapper, accountEntryMapper, bookMapper, bookAccessService);
        when(bookMapper.selectById(1L)).thenReturn(book());
    }

    @Test
    void shouldSeparateAssetsLiabilitiesAndNetAssets() {
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                account(1L, "ASSET", "1000.00"),
                account(2L, "ASSET", "-100.00"),
                account(3L, "LIABILITY", "-300.00"),
                account(4L, "LIABILITY", "50.00")));

        AssetSummaryOutDTO result = service.summary(7L, 1L);

        verify(bookAccessService).requireMember(7L, 1L);
        assertEquals(new BigDecimal("900.00"), result.totalAssets());
        assertEquals(new BigDecimal("300.00"), result.totalLiabilities());
        assertEquals(new BigDecimal("650.00"), result.netAssets());
        assertEquals(2, result.assetAccounts().size());
        assertEquals(2, result.liabilityAccounts().size());
    }

    @Test
    void shouldReverseEntriesAfterHistoricalMonthEnd() {
        YearMonth current = YearMonth.now(ZoneId.of("Asia/Shanghai"));
        AccountEntity account = account(1L, "ASSET", "1000.00");
        account.setCreatedTime(current.minusMonths(2).atDay(1).atStartOfDay());
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setAccountId(1L);
        entry.setSignedAmount(new BigDecimal("100.00"));
        entry.setHappenedAt(current.atDay(1).atStartOfDay());
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(List.of(account));
        when(accountEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entry));

        AssetTrendOutDTO result = service.trend(7L, 1L, 2);

        assertEquals(current.minusMonths(1).toString(), result.points().getFirst().month());
        assertEquals(new BigDecimal("900.00"), result.points().getFirst().netAssets());
        assertEquals(new BigDecimal("1000.00"), result.points().getLast().netAssets());
    }

    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setTimezone("Asia/Shanghai");
        book.setCurrencyCode("CNY");
        book.setStatus(1);
        return book;
    }

    private AccountEntity account(Long id, String nature, String balance) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setName("账户" + id);
        account.setAccountType("BANK");
        account.setAccountNature(nature);
        account.setCurrentBalance(new BigDecimal(balance));
        account.setCreatedTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        return account;
    }
}
