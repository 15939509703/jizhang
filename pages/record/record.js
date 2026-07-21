Page({
  data: {
    navItems: [],
    form: { type: 'expense', title: '午餐外卖', amount: '28', category: '餐饮', account: '微信支付', date: '', time: '', note: '和同事午餐', bookId: 'book_personal' },
  },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.setData({ navItems: store.loadDashboardData().navItems })
  },

  handleInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      form: {
        ...this.data.form,
        [field]: event.detail.value,
      },
    })
  },

  handleSave() {
    getApp().dataStore.addBill({ ...this.data.form, date: new Date().toISOString() })
    wx.showToast({ title: '已保存', icon: 'success' })
    wx.redirectTo({ url: '/pages/bills/bills' })
  },
})
