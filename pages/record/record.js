const { getAccounts } = require('../../api/accounts')
const { getCategories } = require('../../api/categories')
const { createTransaction } = require('../../api/transactions')

function pad(value) {
  return String(value).padStart(2, '0')
}

function initialForm() {
  const now = new Date()
  return {
    amount: '',
    date: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`,
    note: '',
    time: `${pad(now.getHours())}:${pad(now.getMinutes())}`,
    title: '',
    transactionType: 'EXPENSE',
  }
}

Page({
  data: {
    accountIndex: 0,
    accounts: [],
    categoryIndex: 0,
    categories: [],
    form: initialForm(),
    loading: false,
    navItems: [],
    submitting: false,
    targetAccountIndex: 0,
    typeOptions: [
      { label: '支出', value: 'EXPENSE' },
      { label: '收入', value: 'INCOME' },
      { label: '转账', value: 'TRANSFER' },
    ],
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.setData({ navItems: store.loadDashboardData().navItems })
    this.loadReferenceData()
  },

  loadReferenceData() {
    const defaultBook = getApp().dataStore.getDefaultBook()
    if (!defaultBook || !defaultBook.id) {
      wx.showToast({ title: '请先选择账本', icon: 'none' })
      return
    }
    const type = this.data.form.transactionType
    this.setData({ loading: true })
    Promise.all([
      getAccounts(defaultBook.id),
      type === 'TRANSFER' ? Promise.resolve([]) : getCategories(defaultBook.id, type),
    ]).then(([accounts, categories]) => {
      this.setData({
        accountIndex: Math.min(this.data.accountIndex, Math.max(accounts.length - 1, 0)),
        accounts,
        categories,
        categoryIndex: Math.min(this.data.categoryIndex, Math.max(categories.length - 1, 0)),
        targetAccountIndex: accounts.length > 1 ? 1 : 0,
      })
    }).catch((error) => {
      this.handleRequestError(error, '基础数据加载失败')
    }).finally(() => {
      this.setData({ loading: false })
    })
  },

  handleTypeChange(event) {
    const transactionType = event.currentTarget.dataset.type
    if (transactionType === this.data.form.transactionType) return
    this.setData({
      categoryIndex: 0,
      form: Object.assign({}, this.data.form, { transactionType }),
    })
    this.loadReferenceData()
  },

  handleInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ form: Object.assign({}, this.data.form, { [field]: event.detail.value }) })
  },

  handlePickerChange(event) {
    this.setData({ [event.currentTarget.dataset.field]: Number(event.detail.value) })
  },

  handleDateChange(event) {
    this.setData({ form: Object.assign({}, this.data.form, { date: event.detail.value }) })
  },

  handleTimeChange(event) {
    this.setData({ form: Object.assign({}, this.data.form, { time: event.detail.value }) })
  },

  validateForm() {
    const amount = Number(this.data.form.amount)
    if (!amount || amount <= 0 || !/^\d+(\.\d{1,2})?$/.test(this.data.form.amount)) {
      return '请输入大于0且最多两位小数的金额'
    }
    if (!this.data.form.title.trim()) return '请输入账单标题'
    if (!this.data.accounts.length) return '当前账本没有可用账户'
    if (this.data.form.transactionType !== 'TRANSFER' && !this.data.categories.length) return '当前类型没有可用分类'
    if (this.data.form.transactionType === 'TRANSFER') {
      if (this.data.accounts.length < 2) return '转账至少需要两个账户'
      if (this.data.accountIndex === this.data.targetAccountIndex) return '转出和转入账户不能相同'
    }
    return ''
  },

  buildPayload() {
    const form = this.data.form
    const category = this.data.categories[this.data.categoryIndex]
    const account = this.data.accounts[this.data.accountIndex]
    const targetAccount = this.data.accounts[this.data.targetAccountIndex]
    const defaultBook = getApp().dataStore.getDefaultBook()
    return {
      accountId: account.id,
      amount: Number(form.amount).toFixed(2),
      bookId: defaultBook.id,
      categoryId: form.transactionType === 'TRANSFER' ? null : category.id,
      happenedAt: new Date(`${form.date}T${form.time}:00`).toISOString(),
      note: form.note.trim() || null,
      requestId: `MINI_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`,
      targetAccountId: form.transactionType === 'TRANSFER' ? targetAccount.id : null,
      title: form.title.trim(),
      transactionType: form.transactionType,
    }
  },

  handleSave() {
    if (this.data.submitting) return
    const validationMessage = this.validateForm()
    if (validationMessage) {
      wx.showToast({ title: validationMessage, icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    createTransaction(this.buildPayload()).then(() => {
      wx.showToast({ title: '记账成功', icon: 'success' })
      setTimeout(() => wx.redirectTo({ url: '/pages/bills/bills' }), 500)
    }).catch((error) => {
      this.handleRequestError(error, '记账失败')
    }).finally(() => {
      this.setData({ submitting: false })
    })
  },

  handleRequestError(error, fallback) {
    if (error.statusCode === 401) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    wx.showToast({ title: error.message || fallback, icon: 'none' })
  },
})
