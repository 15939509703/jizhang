const { getTransactionSummary } = require('../../api/transactions')

function formatCurrency(value, currencyCode) {
  const symbols = { CNY: '¥', USD: '$', EUR: '€', GBP: '£', JPY: '¥' }
  const symbol = symbols[currencyCode] || `${currencyCode || 'CNY'} `
  const amount = Number(value || 0)
  const sign = amount < 0 ? '-' : ''
  return `${sign}${symbol}${Math.abs(amount).toFixed(2)}`
}

function formatMonthLabel(month) {
  const parts = String(month || '').split('-')
  return parts.length === 2 ? `${Number(parts[1])}月` : ''
}

Page({
  data: {
    userName: '',
    todayLabel: '',
    monthLabel: '',
    summary: { incomeText: '¥0.00', expenseText: '¥0.00', balanceText: '¥0.00', todayIncomeText: '¥0.00', todayExpenseText: '¥0.00' },
    recentBills: [],
    budgets: [],
    navItems: [],
    summaryLoading: false,
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.refresh()
  },

  refresh() {
    const store = getApp().dataStore
    const data = store.loadDashboardData()
    this.setData({
      userName: data.user && data.user.nickName ? data.user.nickName : '记账用户',
      todayLabel: data.todayLabel,
      monthLabel: data.monthLabel,
      recentBills: data.recentBills,
      budgets: data.budgets,
      navItems: data.navItems,
    })
    const defaultBook = store.getDefaultBook()
    if (!defaultBook || !defaultBook.id || !store.getAccessToken()) {
      return
    }
    const requestId = Date.now()
    this.summaryRequestId = requestId
    this.setData({ summaryLoading: true })
    getTransactionSummary(defaultBook.id)
      .then((summary) => {
        if (this.summaryRequestId !== requestId) return
        this.setData({
          monthLabel: formatMonthLabel(summary.month) || data.monthLabel,
          summary: {
            incomeText: formatCurrency(summary.income, summary.currencyCode),
            expenseText: formatCurrency(summary.expense, summary.currencyCode),
            balanceText: formatCurrency(summary.balance, summary.currencyCode),
            todayIncomeText: '¥0.00',
            todayExpenseText: '¥0.00',
          },
        })
      })
      .catch((error) => {
        if (error.statusCode === 401) {
          wx.reLaunch({ url: '/pages/login/login' })
          return
        }
        wx.showToast({ title: error.message || '首页数据加载失败', icon: 'none' })
      })
      .finally(() => {
        if (this.summaryRequestId === requestId) {
          this.setData({ summaryLoading: false })
        }
      })
  },
})
