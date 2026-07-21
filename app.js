const STORAGE_KEYS = {
  USER_INFO: 'jz_user_info',
  ACCESS_TOKEN: 'jz_access_token',
  REFRESH_TOKEN: 'jz_refresh_token',
  DEFAULT_BOOK: 'jz_default_book',
  BILLS: 'jz_bills',
  ACCOUNTS: 'jz_accounts',
  BOOKS: 'jz_books',
  BUDGETS: 'jz_budgets',
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function buildDate(offsetDays, hours, minutes) {
  const date = new Date()
  date.setDate(date.getDate() - offsetDays)
  date.setHours(hours, minutes, 0, 0)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(hours)}:${pad(minutes)}:00`
}

function parseDate(dateValue) {
  if (dateValue instanceof Date) {
    return dateValue
  }
  if (typeof dateValue === 'string') {
    let normalized = dateValue.replace(' ', 'T')
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(normalized)) {
      normalized += ':00'
    }
    return new Date(normalized)
  }
  return new Date(dateValue)
}

const defaultUser = {
  nickName: 'LHJ',
  avatarUrl: '',
  code: '',
  loggedIn: false,
  loginAt: '',
}

const defaultAccounts = [
  { id: 'account_1', name: '工商银行卡', type: 'bank', balance: 21860, note: '主账户' },
  { id: 'account_2', name: '微信支付', type: 'wallet', balance: 3240, note: '高频消费' },
  { id: 'account_3', name: '支付宝', type: 'wallet', balance: 5120, note: '常用' },
  { id: 'account_4', name: '信用卡', type: 'credit', balance: -6380, note: '待还款' },
]

const defaultBooks = [
  { id: 'book_personal', name: '个人账本', memberCount: 1, current: true, desc: '默认账本' },
  { id: 'book_family', name: '家庭账本', memberCount: 3, current: false, desc: '共同记账' },
]

const defaultBudgets = [
  { id: 'budget_food', name: '餐饮', limit: 1800, used: 1296, warnRate: 0.8 },
  { id: 'budget_shop', name: '购物', limit: 1500, used: 1020, warnRate: 0.8 },
  { id: 'budget_traffic', name: '交通', limit: 600, used: 210, warnRate: 0.8 },
]

const defaultBills = [
  { id: 'bill_1001', type: 'expense', category: '餐饮', title: '午餐外卖', amount: 28, account: '微信支付', date: buildDate(0, 12, 30), note: '和同事午餐', bookId: 'book_personal' },
  { id: 'bill_1002', type: 'income', category: '工资', title: '月度工资', amount: 12000, account: '工商银行卡', date: buildDate(1, 9, 10), note: '工资到账', bookId: 'book_personal' },
  { id: 'bill_1003', type: 'expense', category: '交通', title: '地铁出行', amount: 6, account: '支付宝', date: buildDate(0, 8, 15), note: '通勤', bookId: 'book_personal' },
]

const mainNavItems = [
  { key: 'home', label: '首页', icon: '首', url: '/pages/home/home' },
  { key: 'bills', label: '账单', icon: '账', url: '/pages/bills/bills' },
  { key: 'record', label: '记一笔', icon: '记', url: '/pages/record/record' },
  { key: 'stats', label: '统计', icon: '统', url: '/pages/stats/stats' },
  { key: 'settings', label: '我的', icon: '我', url: '/pages/settings/settings' },
]

function getStorage(key, fallback) {
  try {
    const value = wx.getStorageSync(key)
    return typeof value === 'undefined' || value === '' ? fallback : value
  } catch (error) {
    return fallback
  }
}

function setStorage(key, value) {
  wx.setStorageSync(key, value)
}

function removeStorage(key) {
  wx.removeStorageSync(key)
}

function formatCurrency(value) {
  const number = Number(value || 0)
  const sign = number < 0 ? '-' : ''
  return `${sign}¥${Math.abs(number).toFixed(2)}`
}

function formatSignedCurrency(value) {
  const number = Number(value || 0)
  const sign = number >= 0 ? '+' : '-'
  return `${sign}¥${Math.abs(number).toFixed(2)}`
}

function formatDate(dateValue) {
  const date = parseDate(dateValue)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatHomeDate() {
  const date = new Date()
  const weekLabels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return {
    todayLabel: `${date.getMonth() + 1}月${date.getDate()}日 ${weekLabels[date.getDay()]}`,
    monthLabel: `${date.getMonth() + 1}月`,
  }
}

function getMonthSummary(bills) {
  const now = new Date()
  const month = now.getMonth()
  const year = now.getFullYear()
  const monthBills = bills.filter((bill) => {
    const date = parseDate(bill.date)
    return date.getMonth() === month && date.getFullYear() === year
  })
  const totalIncome = monthBills.filter((bill) => bill.type === 'income').reduce((sum, bill) => sum + Number(bill.amount), 0)
  const totalExpense = monthBills.filter((bill) => bill.type === 'expense').reduce((sum, bill) => sum + Number(bill.amount), 0)
  return { totalIncome, totalExpense, balance: totalIncome - totalExpense, todayIncome: 0, todayExpense: 0 }
}

function groupBillsByDay(bills) {
  const map = {}
  bills.forEach((bill) => {
    const date = parseDate(bill.date)
    const key = `${date.getMonth() + 1}月${date.getDate()}日`
    if (!map[key]) {
      map[key] = []
    }
    map[key].push(bill)
  })
  return Object.keys(map).map((dateLabel) => ({
    dateLabel,
    total: map[dateLabel].reduce((sum, bill) => sum + Number(bill.type === 'expense' ? bill.amount : 0), 0),
    items: map[dateLabel],
  }))
}

function getCategorySummary(bills, type) {
  const filtered = bills.filter((bill) => bill.type === type)
  const total = filtered.reduce((sum, bill) => sum + Number(bill.amount), 0)
  const categoryMap = {}
  filtered.forEach((bill) => {
    categoryMap[bill.category] = (categoryMap[bill.category] || 0) + Number(bill.amount)
  })
  return Object.keys(categoryMap).map((name) => ({
    name,
    value: categoryMap[name],
    percent: total > 0 ? Math.round((categoryMap[name] / total) * 100) : 0,
  }))
}

function getBudgetUsage(budgets) {
  return budgets.map((budget) => {
    const percent = budget.limit > 0 ? Math.round((budget.used / budget.limit) * 100) : 0
    return Object.assign({}, budget, {
      percent,
      status: percent >= 100 ? 'over' : percent >= Math.round(budget.warnRate * 100) ? 'warn' : 'ok',
    })
  })
}

function getTrendSeries(bills) {
  const today = new Date()
  const result = []
  for (let offset = 6; offset >= 0; offset -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - offset)
    const expense = bills
      .filter((bill) => bill.type === 'expense')
      .filter((bill) => {
        const billDate = parseDate(bill.date)
        return billDate.getMonth() === date.getMonth() && billDate.getDate() === date.getDate()
      })
      .reduce((sum, bill) => sum + Number(bill.amount), 0)
    result.push({ key: `${date.getMonth() + 1}-${date.getDate()}`, value: expense })
  }
  return result
}

const dataStore = {
  ensureSeedData() {
    if (!getStorage(STORAGE_KEYS.BILLS, null)) setStorage(STORAGE_KEYS.BILLS, defaultBills)
    if (!getStorage(STORAGE_KEYS.ACCOUNTS, null)) setStorage(STORAGE_KEYS.ACCOUNTS, defaultAccounts)
    if (!getStorage(STORAGE_KEYS.BOOKS, null)) setStorage(STORAGE_KEYS.BOOKS, defaultBooks)
    if (!getStorage(STORAGE_KEYS.BUDGETS, null)) setStorage(STORAGE_KEYS.BUDGETS, defaultBudgets)
    if (!getStorage(STORAGE_KEYS.USER_INFO, null)) setStorage(STORAGE_KEYS.USER_INFO, defaultUser)
  },

  getStoredUser() {
    return getStorage(STORAGE_KEYS.USER_INFO, defaultUser)
  },

  saveUser(userInfo) {
    setStorage(STORAGE_KEYS.USER_INFO, userInfo)
  },

  saveLoginSession(loginResult) {
    const user = Object.assign({}, loginResult.user, {
      loggedIn: true,
      loginAt: new Date().toISOString(),
      isMock: false,
    })
    setStorage(STORAGE_KEYS.ACCESS_TOKEN, loginResult.accessToken)
    setStorage(STORAGE_KEYS.REFRESH_TOKEN, loginResult.refreshToken)
    setStorage(STORAGE_KEYS.DEFAULT_BOOK, loginResult.defaultBook)
    setStorage(STORAGE_KEYS.USER_INFO, user)
    return user
  },

  getAccessToken() {
    return getStorage(STORAGE_KEYS.ACCESS_TOKEN, '')
  },

  getDefaultBook() {
    return getStorage(STORAGE_KEYS.DEFAULT_BOOK, null)
  },

  hasValidLocalSession() {
    const user = getStorage(STORAGE_KEYS.USER_INFO, null)
    return !!(user && user.loggedIn && (user.isMock || this.getAccessToken()))
  },

  clearAuthSession() {
    removeStorage(STORAGE_KEYS.ACCESS_TOKEN)
    removeStorage(STORAGE_KEYS.REFRESH_TOKEN)
    removeStorage(STORAGE_KEYS.DEFAULT_BOOK)
    removeStorage(STORAGE_KEYS.USER_INFO)
  },

  clearUser() {
    this.clearAuthSession()
  },

  ensureLoggedIn() {
    if (!this.hasValidLocalSession()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return false
    }
    return true
  },

  getBills() {
    return getStorage(STORAGE_KEYS.BILLS, [])
  },

  addBill(payload) {
    const bills = this.getBills()
    bills.unshift(Object.assign({ id: `bill_${Date.now()}` }, payload, { amount: Number(payload.amount) }))
    setStorage(STORAGE_KEYS.BILLS, bills)
  },

  deleteBill(billId) {
    setStorage(STORAGE_KEYS.BILLS, this.getBills().filter((bill) => bill.id !== billId))
  },

  loadDashboardData() {
    const bills = this.getBills()
    const summary = getMonthSummary(bills)
    const budgets = getBudgetUsage(getStorage(STORAGE_KEYS.BUDGETS, []))
    const dateInfo = formatHomeDate()
    return {
      user: this.getStoredUser(),
      todayLabel: dateInfo.todayLabel,
      monthLabel: dateInfo.monthLabel,
      summary: Object.assign({}, summary, {
        incomeText: formatCurrency(summary.totalIncome),
        expenseText: formatCurrency(summary.totalExpense),
        balanceText: formatCurrency(summary.balance),
        todayIncomeText: formatCurrency(summary.todayIncome),
        todayExpenseText: formatCurrency(summary.todayExpense),
      }),
      recentBills: bills.slice(0, 5).map((bill) => Object.assign({}, bill, {
        amountText: formatCurrency(bill.amount),
        amountSignedText: formatSignedCurrency(bill.type === 'income' ? bill.amount : -bill.amount),
        dateText: formatDate(bill.date),
      })),
      budgets,
      navItems: mainNavItems,
    }
  },

  loadBillsData() {
    const bills = this.getBills()
    return {
      groupedBills: groupBillsByDay(bills).map((group) => Object.assign({}, group, {
        totalText: formatCurrency(group.total),
        items: group.items.map((bill) => Object.assign({}, bill, {
          amountSignedText: formatSignedCurrency(bill.type === 'income' ? bill.amount : -bill.amount),
          amountClass: bill.type === 'income' ? 'success' : 'danger',
          dateText: formatDate(bill.date),
        })),
      })),
      navItems: mainNavItems,
    }
  },

  loadStatsData() {
    const bills = this.getBills()
    const expenseSummary = getCategorySummary(bills, 'expense')
    const incomeSummary = getCategorySummary(bills, 'income')
    const trendSeries = getTrendSeries(bills)
    const monthSummary = getMonthSummary(bills)
    const maxTrend = Math.max.apply(null, trendSeries.map((item) => item.value).concat([1]))
    return {
      summary: {
        incomeText: formatCurrency(monthSummary.totalIncome),
        expenseText: formatCurrency(monthSummary.totalExpense),
        balanceText: formatCurrency(monthSummary.balance),
      },
      expenseSummary: expenseSummary.map((item) => Object.assign({}, item, { valueText: formatCurrency(item.value) })),
      incomeSummary: incomeSummary.map((item) => Object.assign({}, item, { valueText: formatCurrency(item.value) })),
      trendSeries: trendSeries.map((item) => Object.assign({}, item, {
        height: Math.max(12, Math.round((item.value / maxTrend) * 100)),
        valueText: formatCurrency(item.value),
      })),
      navItems: mainNavItems,
    }
  },

  loadBudgetData() {
    return {
      budgets: getBudgetUsage(getStorage(STORAGE_KEYS.BUDGETS, [])).map((budget) => Object.assign({}, budget, {
        limitText: formatCurrency(budget.limit),
        usedText: formatCurrency(budget.used),
        statusClass: budget.status === 'over' ? 'danger' : budget.status === 'warn' ? 'warn' : 'success',
      })),
      navItems: mainNavItems,
    }
  },

  loadAccountsData() {
    const accounts = getStorage(STORAGE_KEYS.ACCOUNTS, []).map((account) => Object.assign({}, account, {
      balanceText: formatCurrency(account.balance),
      balanceClass: Number(account.balance) >= 0 ? 'success' : 'danger',
      typeText: account.type === 'bank' ? '银行卡' : account.type === 'wallet' ? '钱包' : '信用卡',
    }))
    return {
      accounts,
      totalBalanceText: formatCurrency(accounts.reduce((sum, account) => sum + Number(account.balance), 0)),
      navItems: mainNavItems,
    }
  },

  loadBooksData() {
    return {
      books: getStorage(STORAGE_KEYS.BOOKS, []),
      navItems: mainNavItems,
    }
  },

  loadSettingsData() {
    return {
      user: this.getStoredUser(),
      navItems: mainNavItems,
    }
  },
}

App({
  dataStore,
  globalData: {
    userInfo: null,
  },

  onLaunch() {
    this.dataStore.ensureSeedData()
    this.globalData.userInfo = this.dataStore.getStoredUser()
  },
})
