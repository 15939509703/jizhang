package com.lhj.jizhang.user.model;

/** 账本成员可配置的业务模块白名单。 */
public enum BookMenu {
    BILLS, REPORTS, CONSUMPTION_REPORT, BUDGET, ACCOUNTS, ASSETS,
    CATEGORIES, RECURRING, LOANS, REIMBURSEMENTS, SAVINGS_GOALS,
    DATA_SECURITY, FAMILY_SHARING, NOTIFICATIONS;

    /** 返回对外稳定的菜单配置键。 */
    public String key() {
        return "MENU." + name();
    }
}
