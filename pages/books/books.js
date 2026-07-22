const { createBook, getBooks } = require('../../api/books')

Page({
  data: {
    books: [],
    creating: false,
    loading: false,
    navItems: [],
    newBook: { description: '', name: '' },
    showCreate: false,
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.setData({ navItems: store.loadDashboardData().navItems })
    this.refresh()
  },

  refresh() {
    const store = getApp().dataStore
    const current = store.getDefaultBook()
    this.setData({ loading: true })
    getBooks().then((books) => {
      this.setData({ books: books.map((book) => Object.assign({}, book, { current: !!current && current.id === book.id })) })
    }).catch((error) => this.handleError(error, '账本加载失败'))
      .finally(() => this.setData({ loading: false }))
  },

  toggleCreate() {
    this.setData({ showCreate: !this.data.showCreate })
  },

  handleInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ newBook: Object.assign({}, this.data.newBook, { [field]: event.detail.value }) })
  },

  handleCreate() {
    const form = this.data.newBook
    if (!form.name.trim()) {
      wx.showToast({ title: '请输入账本名称', icon: 'none' })
      return
    }
    this.setData({ creating: true })
    createBook({
      currencyCode: 'CNY',
      description: form.description.trim() || null,
      name: form.name.trim(),
      timezone: 'Asia/Shanghai',
    }).then((book) => {
      getApp().dataStore.setDefaultBook(book)
      wx.showToast({ title: '账本已创建', icon: 'success' })
      this.setData({ newBook: { description: '', name: '' }, showCreate: false })
      this.refresh()
    }).catch((error) => this.handleError(error, '账本创建失败'))
      .finally(() => this.setData({ creating: false }))
  },

  handleSwitch(event) {
    const book = this.data.books.find((item) => item.id === event.currentTarget.dataset.id)
    if (!book || book.current) return
    getApp().dataStore.setDefaultBook(book)
    this.setData({ books: this.data.books.map((item) => Object.assign({}, item, { current: item.id === book.id })) })
    wx.showToast({ title: `已切换到${book.name}`, icon: 'none' })
  },

  handleError(error, fallback) {
    if (error.statusCode === 401) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    wx.showToast({ title: error.message || fallback, icon: 'none' })
  },
})
