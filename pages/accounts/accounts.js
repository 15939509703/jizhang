Page({
  data: { navItems: [], accounts: [], totalBalanceText: '¥0.00' },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    const data = store.loadAccountsData()
    this.setData({ navItems: data.navItems, accounts: data.accounts, totalBalanceText: data.totalBalanceText })
  },
})
