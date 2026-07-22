const { getAccounts } = require('../../api/accounts')
const { getCategories } = require('../../api/categories')
const { getTransactionSummary, getTransactions } = require('../../api/transactions')
const { formatCurrency, mapTransaction } = require('../../utils/transaction-view')

function formatMonthLabel(month) {
  const parts = String(month || '').split('-')
  return parts.length === 2 ? `${Number(parts[1])}月` : ''
}

function toNameMap(items) {
  return (items || []).reduce((result, item) => {
    result[item.id] = item.name
    return result
  }, {})
}

Page({
  data: {
    budgets: [],
    monthLabel: '',
    navItems: [],
    recentBills: [],
    summary: { balanceText: '¥0.00', expenseText: '¥0.00', incomeText: '¥0.00' },
    summaryLoading: false,
    todayLabel: '',
    userName: '',
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.refresh()
  },

  refresh() {
    const store = getApp().dataStore
    const localData = store.loadDashboardData()
    this.setData({
      budgets: localData.budgets,
      monthLabel: localData.monthLabel,
      navItems: localData.navItems,
      todayLabel: localData.todayLabel,
      userName: localData.user && localData.user.nickName ? localData.user.nickName : '记账用户',
    })
    const defaultBook = store.getDefaultBook()
    if (!defaultBook || !defaultBook.id || !store.getAccessToken()) return
    this.setData({ summaryLoading: true })
    Promise.all([
      getTransactionSummary(defaultBook.id),
      getTransactions({ bookId: defaultBook.id, limit: 5, status: 'EFFECTIVE' }),
      getAccounts(defaultBook.id),
      getCategories(defaultBook.id),
    ]).then(([summary, page, accounts, categories]) => {
      const accountMap = toNameMap(accounts)
      const categoryMap = toNameMap(categories)
      this.setData({
        monthLabel: formatMonthLabel(summary.month) || localData.monthLabel,
        recentBills: (page.items || []).map((item) => mapTransaction(item, categoryMap, accountMap)),
        summary: {
          balanceText: formatCurrency(summary.balance, summary.currencyCode),
          expenseText: formatCurrency(summary.expense, summary.currencyCode),
          incomeText: formatCurrency(summary.income, summary.currencyCode),
        },
      })
    }).catch((error) => this.handleError(error, '首页数据加载失败'))
      .finally(() => this.setData({ summaryLoading: false }))
  },

  goRecord() {
    wx.redirectTo({ url: '/pages/record/record' })
  },

  goBills() {
    wx.redirectTo({ url: '/pages/bills/bills' })
  },

  handleError(error, fallback) {
    if (error.statusCode === 401) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    wx.showToast({ title: error.message || fallback, icon: 'none' })
  },
})
