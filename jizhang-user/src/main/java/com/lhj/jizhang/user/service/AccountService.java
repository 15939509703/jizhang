package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.AccountCreateInDTO;
import com.lhj.jizhang.user.dto.AccountOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
    private final AccountMapper accountMapper;
    private final BookAccessService bookAccessService;

    public AccountService(AccountMapper accountMapper, BookAccessService bookAccessService) {
        this.accountMapper = accountMapper;
        this.bookAccessService = bookAccessService;
    }

    public List<AccountOutDTO> list(Long userId, Long bookId) {
        bookAccessService.requireMember(userId, bookId);
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getBookId, bookId)
                        .eq(AccountEntity::getDeletedFlag, 0)
                        .orderByAsc(AccountEntity::getSortNo, AccountEntity::getId)).stream()
                .map(this::toOutput)
                .toList();
    }

    @Transactional
    public AccountOutDTO create(Long userId, AccountCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        AccountEntity account = new AccountEntity();
        account.setAccountNo(BusinessIdGenerator.next("ACC_"));
        account.setBookId(input.bookId());
        account.setName(input.name().trim());
        account.setAccountType(input.accountType());
        account.setAccountNature(input.accountNature());
        account.setInitialBalance(input.initialBalance());
        account.setCurrentBalance(input.initialBalance());
        account.setIncludedInAssets(Boolean.TRUE.equals(input.includedInAssets()) ? 1 : 0);
        account.setSortNo(100);
        account.setStatus(1);
        account.setVersion(0);
        account.setDeletedFlag(0);
        account.setCreator(String.valueOf(userId));
        account.setModifier(String.valueOf(userId));
        accountMapper.insert(account);
        return toOutput(account);
    }

    private AccountOutDTO toOutput(AccountEntity account) {
        return new AccountOutDTO(account.getId(), account.getAccountNo(), account.getBookId(), account.getName(),
                account.getAccountType(), account.getAccountNature(), account.getInitialBalance(),
                account.getCurrentBalance(), account.getIncludedInAssets() == 1, account.getStatus(),
                account.getVersion());
    }
}
