const { createAccount, getAccounts } = require('../../api/accounts')
const { accountTypeLabel, formatCurrency } = require('../../utils/transaction-view')

Page({
  data: {
    accounts: [],
    creating: false,
    loading: false,
    navItems: [],
    newAccount: { initialBalance: '0.00', name: '', typeIndex: 0 },
    showCreate: false,
    totalBalanceText: '¥0.00',
    typeOptions: [
      { accountNature: 'ASSET', label: '现金', value: 'CASH' },
      { accountNature: 'ASSET', label: '银行卡', value: 'BANK' },
      { accountNature: 'ASSET', label: '微信', value: 'WECHAT' },
      { accountNature: 'ASSET', label: '支付宝', value: 'ALIPAY' },
      { accountNature: 'LIABILITY', label: '信用卡', value: 'CREDIT' },
    ],
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
    this.bookId = defaultBook.id
    this.setData({ loading: true })
    getAccounts(this.bookId).then((accounts) => {
      const items = accounts.map((account) => Object.assign({}, account, {
        balanceClass: Number(account.currentBalance) >= 0 ? 'success' : 'danger',
        balanceText: formatCurrency(account.currentBalance),
        typeText: accountTypeLabel(account.accountType),
      }))
      const total = accounts.filter((account) => account.includedInAssets)
        .reduce((sum, account) => sum + Number(account.currentBalance), 0)
      this.setData({ accounts: items, totalBalanceText: formatCurrency(total) })
    }).catch((error) => this.handleError(error, '账户加载失败'))
      .finally(() => this.setData({ loading: false }))
  },

  toggleCreate() {
    this.setData({ showCreate: !this.data.showCreate })
  },

  handleInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ newAccount: Object.assign({}, this.data.newAccount, { [field]: event.detail.value }) })
  },

  handleTypeChange(event) {
    this.setData({ newAccount: Object.assign({}, this.data.newAccount, { typeIndex: Number(event.detail.value) }) })
  },

  handleCreate() {
    const form = this.data.newAccount
    const type = this.data.typeOptions[form.typeIndex]
    if (!form.name.trim()) {
      wx.showToast({ title: '请输入账户名称', icon: 'none' })
      return
    }
    if (!/^-?\d+(\.\d{1,2})?$/.test(form.initialBalance)) {
      wx.showToast({ title: '初始余额格式不正确', icon: 'none' })
      return
    }
    this.setData({ creating: true })
    createAccount({
      accountNature: type.accountNature,
      accountType: type.value,
      bookId: this.bookId,
      includedInAssets: type.accountNature === 'ASSET',
      initialBalance: Number(form.initialBalance).toFixed(2),
      name: form.name.trim(),
    }).then(() => {
      wx.showToast({ title: '账户已创建', icon: 'success' })
      this.setData({ newAccount: { initialBalance: '0.00', name: '', typeIndex: 0 }, showCreate: false })
      this.refresh()
    }).catch((error) => this.handleError(error, '账户创建失败'))
      .finally(() => this.setData({ creating: false }))
  },

  handleError(error, fallback) {
    if (error.statusCode === 401) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    wx.showToast({ title: error.message || fallback, icon: 'none' })
  },
})
