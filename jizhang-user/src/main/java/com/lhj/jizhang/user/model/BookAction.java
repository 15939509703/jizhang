package com.lhj.jizhang.user.model;

/** 原六项操作以外的业务动作与纯界面工具键。 */
public enum BookAction {
    BILLS_SEARCH(BookMenu.BILLS, true), BILLS_FILTER(BookMenu.BILLS, true),
    BILLS_VOID(BookMenu.BILLS, false), BILLS_ATTACHMENT_UPLOAD(BookMenu.BILLS, false),
    BILLS_ATTACHMENT_DELETE(BookMenu.BILLS, false), BUDGET_MANAGE(BookMenu.BUDGET, false),
    ACCOUNTS_MANAGE(BookMenu.ACCOUNTS, false), ACCOUNTS_ADJUST(BookMenu.ACCOUNTS, false),
    CATEGORIES_MANAGE(BookMenu.CATEGORIES, false), RECURRING_MANAGE(BookMenu.RECURRING, false),
    RECURRING_CONFIRM(BookMenu.RECURRING, false), LOANS_MANAGE(BookMenu.LOANS, false),
    LOANS_REPAY(BookMenu.LOANS, false), LOANS_REMIND(BookMenu.LOANS, false),
    REIMBURSEMENTS_MANAGE(BookMenu.REIMBURSEMENTS, false),
    REIMBURSEMENTS_RECEIVE(BookMenu.REIMBURSEMENTS, false),
    SAVINGS_GOALS_MANAGE(BookMenu.SAVINGS_GOALS, false),
    SAVINGS_GOALS_CONTRIBUTE(BookMenu.SAVINGS_GOALS, false),
    DATA_SECURITY_BACKUP(BookMenu.DATA_SECURITY, false),
    DATA_SECURITY_RESTORE(BookMenu.DATA_SECURITY, false),
    DATA_SECURITY_DUPLICATES(BookMenu.DATA_SECURITY, false),
    NOTIFICATIONS_MANAGE(BookMenu.NOTIFICATIONS, false);

    private final BookMenu menu;
    private final boolean displayOnly;

    BookAction(BookMenu menu, boolean displayOnly) {
        this.menu = menu;
        this.displayOnly = displayOnly;
    }

    /** 返回动作所属的业务模块。 */
    public BookMenu menu() {
        return menu;
    }

    /** 标识不构成服务端数据保护的纯界面工具键。 */
    public boolean displayOnly() {
        return displayOnly;
    }

    /** 返回对外稳定的动作配置键。 */
    public String key() {
        return "ACTION." + menu.name() + "." + name().substring(menu.name().length() + 1);
    }
}
