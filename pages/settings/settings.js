Page({
  data: { navItems: [], user: { nickName: '记账用户', loggedIn: false } },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    const data = store.loadSettingsData()
    this.setData({ navItems: data.navItems, user: data.user })
  },

  handleLogout() {
    getApp().dataStore.clearAuthSession()
    getApp().globalData.userInfo = null
    wx.reLaunch({ url: '/pages/login/login' })
  },
})
