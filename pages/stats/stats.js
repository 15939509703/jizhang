const { getCategories } = require('../../api/categories')
const { getTransactionSummary, getTransactions } = require('../../api/transactions')
const { formatCurrency } = require('../../utils/transaction-view')

function monthRange() {
  const now = new Date()
  return {
    endAt: new Date(now.getFullYear(), now.getMonth() + 1, 1).toISOString(),
    startAt: new Date(now.getFullYear(), now.getMonth(), 1).toISOString(),
  }
}

function categorySummary(items, categories) {
  const names = categories.reduce((result, item) => {
    result[item.id] = item.name
    return result
  }, {})
  const amounts = items.filter((item) => item.transactionType === 'EXPENSE').reduce((result, item) => {
    const name = names[item.categoryId] || '未分类'
    result[name] = (result[name] || 0) + Number(item.amount)
    return result
  }, {})
  const total = Object.keys(amounts).reduce((sum, name) => sum + amounts[name], 0)
  return Object.keys(amounts).map((name) => ({ name, value: amounts[name] }))
    .sort((left, right) => right.value - left.value)
    .map((item) => Object.assign({}, item, {
      percent: total ? Math.round(item.value / total * 100) : 0,
      valueText: formatCurrency(item.value),
    }))
}

function trendSeries(items) {
  const today = new Date()
  const points = []
  for (let offset = 6; offset >= 0; offset -= 1) {
    const target = new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset)
    const value = items.filter((item) => {
      if (item.transactionType !== 'EXPENSE') return false
      const date = new Date(item.happenedAt)
      return date.getFullYear() === target.getFullYear()
        && date.getMonth() === target.getMonth()
        && date.getDate() === target.getDate()
    }).reduce((sum, item) => sum + Number(item.amount), 0)
    points.push({ key: `${target.getMonth() + 1}/${target.getDate()}`, value })
  }
  const max = Math.max.apply(null, points.map((item) => item.value).concat([1]))
  return points.map((item) => Object.assign({}, item, {
    height: Math.max(item.value ? 12 : 4, Math.round(item.value / max * 100)),
    valueText: formatCurrency(item.value),
  }))
}

Page({
  data: {
    expenseSummary: [],
    loading: false,
    navItems: [],
    summary: { balanceText: '¥0.00', expenseText: '¥0.00', incomeText: '¥0.00' },
    trendSeries: [],
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.setData({ navItems: store.loadDashboardData().navItems })
    this.refresh()
  },

  refresh() {
    const defaultBook = getApp().dataStore.getDefaultBook()
    if (!defaultBook || !defaultBook.id) return
    const range = monthRange()
    this.setData({ loading: true })
    Promise.all([
      getTransactionSummary(defaultBook.id),
      getTransactions({ bookId: defaultBook.id, startAt: range.startAt, endAt: range.endAt, limit: 100, status: 'EFFECTIVE' }),
      getCategories(defaultBook.id),
    ]).then(([summary, page, categories]) => {
      const items = page.items || []
      this.setData({
        expenseSummary: categorySummary(items, categories),
        summary: {
          balanceText: formatCurrency(summary.balance, summary.currencyCode),
          expenseText: formatCurrency(summary.expense, summary.currencyCode),
          incomeText: formatCurrency(summary.income, summary.currencyCode),
        },
        trendSeries: trendSeries(items),
      })
    }).catch((error) => this.handleError(error, '统计数据加载失败'))
      .finally(() => this.setData({ loading: false }))
  },

  handleError(error, fallback) {
    if (error.statusCode === 401) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    wx.showToast({ title: error.message || fallback, icon: 'none' })
  },
})
