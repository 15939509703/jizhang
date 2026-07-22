const { getAccounts } = require('../../api/accounts')
const { getCategories } = require('../../api/categories')
const { getTransactions, voidTransaction } = require('../../api/transactions')
const { groupTransactions, mapTransaction } = require('../../utils/transaction-view')

Page({
  data: {
    activeType: '',
    groupedBills: [],
    hasMore: false,
    loading: false,
    loadingMore: false,
    navItems: [],
    typeOptions: [
      { label: '全部', value: '' },
      { label: '支出', value: 'EXPENSE' },
      { label: '收入', value: 'INCOME' },
      { label: '转账', value: 'TRANSFER' },
    ],
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.setData({ navItems: store.loadDashboardData().navItems })
    this.refresh()
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.loadMore()
    }
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh())
  },

  refresh() {
    const defaultBook = getApp().dataStore.getDefaultBook()
    if (!defaultBook || !defaultBook.id) {
      wx.showToast({ title: '请先选择账本', icon: 'none' })
      return Promise.resolve()
    }
    this.bookId = defaultBook.id
    this.setData({ loading: true })
    return Promise.all([
      getAccounts(this.bookId),
      getCategories(this.bookId),
      getTransactions(this.buildQuery()),
    ]).then(([accounts, categories, page]) => {
      this.accountMap = this.toNameMap(accounts)
      this.categoryMap = this.toNameMap(categories)
      this.rawItems = page.items || []
      this.nextCursor = page.nextCursor || null
      this.renderItems(page.hasMore)
    }).catch((error) => {
      this.handleRequestError(error, '账单加载失败')
    }).finally(() => {
      this.setData({ loading: false })
    })
  },

  loadMore() {
    this.setData({ loadingMore: true })
    getTransactions(this.buildQuery(this.nextCursor)).then((page) => {
      this.rawItems = this.rawItems.concat(page.items || [])
      this.nextCursor = page.nextCursor || null
      this.renderItems(page.hasMore)
    }).catch((error) => {
      this.handleRequestError(error, '更多账单加载失败')
    }).finally(() => {
      this.setData({ loadingMore: false })
    })
  },

  buildQuery(cursorId) {
    const query = { bookId: this.bookId, limit: 20, status: 'EFFECTIVE' }
    if (this.data.activeType) query.type = this.data.activeType
    if (cursorId) query.cursorId = cursorId
    return query
  },

  renderItems(hasMore) {
    const bills = this.rawItems.map((item) => mapTransaction(item, this.categoryMap, this.accountMap))
    this.setData({ groupedBills: groupTransactions(bills), hasMore: !!hasMore })
  },

  toNameMap(items) {
    return (items || []).reduce((result, item) => {
      result[item.id] = item.name
      return result
    }, {})
  },

  handleTypeChange(event) {
    const activeType = event.currentTarget.dataset.type
    if (activeType === this.data.activeType) return
    this.setData({ activeType }, () => this.refresh())
  },

  handleVoid(event) {
    const transactionId = event.currentTarget.dataset.id
    wx.showModal({
      title: '作废账单',
      content: '作废后会同步冲正账户余额，确定继续吗？',
      confirmColor: '#d9534f',
      success: (result) => {
        if (!result.confirm) return
        voidTransaction(transactionId).then(() => {
          wx.showToast({ title: '账单已作废', icon: 'success' })
          this.refresh()
        }).catch((error) => {
          this.handleRequestError(error, '账单作废失败')
        })
      },
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
