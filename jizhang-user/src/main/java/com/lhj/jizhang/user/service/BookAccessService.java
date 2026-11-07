package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.model.BookPermission;
import com.lhj.jizhang.user.model.BookMenu;
import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.TransactionScope;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class BookAccessService {
    private final BookMemberMapper bookMemberMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public BookAccessService(BookMemberMapper bookMemberMapper, ObjectMapper objectMapper) {
        this.bookMemberMapper = bookMemberMapper;
        this.objectMapper = objectMapper;
    }

    BookAccessService(BookMemberMapper bookMemberMapper) {
        this(bookMemberMapper, new ObjectMapper());
    }

    public BookMemberEntity requireMember(Long userId, Long bookId) {
        BookMemberEntity member = bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getUserId, userId)
                .eq(BookMemberEntity::getStatus, 1));
        if (member == null) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "无权访问该账本", HttpStatus.FORBIDDEN);
        }
        evaluate(member);
        return member;
    }

    public BookMemberEntity requireWritable(Long userId, Long bookId) {
        return requirePermission(userId, bookId, BookPermission.CREATE_TRANSACTIONS);
    }

    public BookMemberEntity requireAdmin(Long userId, Long bookId) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "仅账本所有者或管理员可执行此操作",
                    HttpStatus.FORBIDDEN);
        }
        if (TransactionScope.OWN_CREATED.name().equals(evaluate(member).transactionScope())) {
            throw denied();
        }
        return member;
    }

    public BookMemberEntity requireOwner(Long userId, Long bookId) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!"OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "仅账本所有者可执行此操作",
                    HttpStatus.FORBIDDEN);
        }
        return member;
    }

    public BookMemberEntity requirePermission(Long userId, Long bookId, BookPermission permission) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!hasPermission(member, permission)) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "当前成员无此操作权限", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    /** 校验账单读取权限并返回当前成员的账单范围。 */
    public TransactionScope requireTransactionScope(Long userId, Long bookId) {
        BookMemberEntity member = requirePermission(userId, bookId, BookPermission.VIEW_TRANSACTIONS);
        return TransactionScope.valueOf(evaluate(member).transactionScope());
    }

    public boolean hasPermission(BookMemberEntity member, BookPermission permission) {
        return evaluate(member).actionPermissions().get(permission.name());
    }

    /** 每次从当前成员记录计算权限，不使用令牌内的角色快照。 */
    public BookAccessOutDTO myAccess(Long userId, Long bookId) {
        return evaluate(requireMember(userId, bookId));
    }

    /** 校验业务模块入口及其数据出口。 */
    public BookMemberEntity requireMenu(Long userId, Long bookId, BookMenu menu) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!evaluate(member).menuPermissions().get(menu.key())) {
            throw denied();
        }
        return member;
    }

    /** 校验指定模块下的业务操作。 */
    public BookMemberEntity requireAction(Long userId, Long bookId, BookAction action) {
        BookMemberEntity member = requireMenu(userId, bookId, action.menu());
        if (!evaluate(member).actionPermissions().get(action.key())) {
            throw denied();
        }
        return member;
    }

    /** 文件包含账单、账户和分类时，要求三个模块与导出操作同时可读。 */
    public BookMemberEntity requireLedgerFileAccess(Long userId, Long bookId) {
        BookMemberEntity member = requirePermission(userId, bookId, BookPermission.EXPORT_DATA);
        BookAccessOutDTO access = evaluate(member);
        if (!access.actionPermissions().get(BookPermission.VIEW_TRANSACTIONS.name())
                || !access.menuPermissions().get(BookMenu.ACCOUNTS.key())
                || !access.menuPermissions().get(BookMenu.CATEGORIES.key())) {
            throw denied();
        }
        return member;
    }

    /** 从成员的显式配置求值；非法策略一律拒绝，不退回角色默认全开。 */
    public BookAccessOutDTO evaluate(BookMemberEntity member) {
        if ("OWNER".equals(member.getRole())) {
            return new BookAccessOutDTO(member.getBookId(), member.getRole(), allMenus(), allActions(),
                    TransactionScope.ALL.name(), member.getPolicyVersion());
        }
        if (member.getRole() == null || !Set.of("ADMIN", "MEMBER", "VIEWER").contains(member.getRole())) {
            throw denied();
        }
        TransactionScope scope = parseScope(member.getTransactionScope());
        Map<String, Boolean> menus = parseFlags(member.getMenuPermissions(), menuKeys(), true);
        Map<String, Boolean> overrides = parseFlags(member.getPermissions(), actionKeys(), false);
        validateDependencies(menus, scope);
        Map<String, Boolean> actions = effectiveActions(member.getRole(), menus, overrides, scope);
        return new BookAccessOutDTO(member.getBookId(), member.getRole(), menus, actions,
                scope.name(), member.getPolicyVersion());
    }

    /** 校验待保存的策略，不接受不完整菜单或不合法动作。 */
    public void validatePolicy(BookMemberEntity member) {
        evaluate(member);
    }

    /** 将数据库范围值转换为受支持的枚举。 */
    private TransactionScope parseScope(String scope) {
        try {
            return TransactionScope.valueOf(scope);
        } catch (RuntimeException exception) {
            throw denied();
        }
    }

    /** 仅解析白名单内的布尔配置，菜单必须完整。 */
    private Map<String, Boolean> parseFlags(String json, Set<String> keys, boolean required) {
        if (!required && (json == null || json.isBlank())) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw denied();
            }
            Map<String, Boolean> flags = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                if (!keys.contains(entry.getKey()) || !entry.getValue().isBoolean()) {
                    throw denied();
                }
                flags.put(entry.getKey(), entry.getValue().booleanValue());
            });
            if (required && !flags.keySet().equals(keys)) {
                throw denied();
            }
            return flags;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw denied();
        }
    }

    /** 拒绝报表缺少明细及受限范围开启不安全模块的配置。 */
    private void validateDependencies(Map<String, Boolean> menus, TransactionScope scope) {
        if (!menus.get(BookMenu.BILLS.key()) && (menus.get(BookMenu.REPORTS.key())
                || menus.get(BookMenu.CONSUMPTION_REPORT.key()))) {
            throw denied();
        }
        if (menus.get(BookMenu.CONSUMPTION_REPORT.key()) && !menus.get(BookMenu.BUDGET.key())) {
            throw denied();
        }
        if (scope == TransactionScope.OWN_CREATED && menus.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(BookMenu.BILLS.key()) && entry.getValue())) {
            throw denied();
        }
    }

    /** 将角色上限、显式操作、菜单与数据范围合成为有效动作。 */
    private Map<String, Boolean> effectiveActions(String role, Map<String, Boolean> menus,
                                                   Map<String, Boolean> overrides, TransactionScope scope) {
        Map<String, Boolean> actions = new LinkedHashMap<>();
        for (BookPermission permission : BookPermission.values()) {
            BookMenu menu = permissionMenu(permission);
            boolean allowed = roleDefault(role, permission) && overrides.getOrDefault(permission.name(), true);
            actions.put(permission.name(), allowed && menus.get(menu.key())
                    && (scope == TransactionScope.ALL || permission == BookPermission.VIEW_TRANSACTIONS));
        }
        for (BookAction action : BookAction.values()) {
            boolean roleAllows = !"VIEWER".equals(role) || action.displayOnly();
            boolean legacyDefault = action.displayOnly() || roleDefault(role, BookPermission.CREATE_TRANSACTIONS)
                    && overrides.getOrDefault(BookPermission.CREATE_TRANSACTIONS.name(), true);
            actions.put(action.key(), roleAllows && overrides.getOrDefault(action.key(), legacyDefault)
                    && menus.get(action.menu().key())
                    && (scope == TransactionScope.ALL || action.displayOnly()));
        }
        return actions;
    }

    /** 关联现有六项操作与对应模块。 */
    private BookMenu permissionMenu(BookPermission permission) {
        return switch (permission) {
            case MANAGE_MEMBERS -> BookMenu.FAMILY_SHARING;
            case EXPORT_DATA -> BookMenu.DATA_SECURITY;
            default -> BookMenu.BILLS;
        };
    }

    /** 返回与现有行为一致的六项角色默认权限。 */
    private boolean roleDefault(String role, BookPermission permission) {
        return switch (role) {
            case "ADMIN" -> true;
            case "MEMBER" -> permission != BookPermission.MANAGE_MEMBERS;
            case "VIEWER" -> permission == BookPermission.VIEW_TRANSACTIONS;
            default -> false;
        };
    }

    /** 返回服务端支持的全部菜单键。 */
    private Set<String> menuKeys() {
        return Arrays.stream(BookMenu.values()).map(BookMenu::key).collect(Collectors.toSet());
    }

    /** 返回六项既有操作及新增动作的全部键。 */
    private Set<String> actionKeys() {
        Set<String> keys = Arrays.stream(BookPermission.values()).map(Enum::name).collect(Collectors.toSet());
        keys.addAll(Arrays.stream(BookAction.values()).map(BookAction::key).collect(Collectors.toSet()));
        return keys;
    }

    /** 构造所有者固定开启的菜单配置。 */
    private Map<String, Boolean> allMenus() {
        Map<String, Boolean> menus = new LinkedHashMap<>();
        for (BookMenu menu : BookMenu.values()) {
            menus.put(menu.key(), true);
        }
        return menus;
    }

    /** 构造所有者固定开启的业务操作配置。 */
    private Map<String, Boolean> allActions() {
        Map<String, Boolean> actions = new LinkedHashMap<>();
        for (String key : actionKeys()) {
            actions.put(key, true);
        }
        return actions;
    }

    /** 返回不暴露策略细节的拒绝访问异常。 */
    private BusinessException denied() {
        return new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "当前成员权限配置无效或无此权限",
                HttpStatus.FORBIDDEN);
    }
}
