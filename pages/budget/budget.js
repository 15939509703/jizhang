Page({
  data: { navItems: [], budgets: [] },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    const data = store.loadBudgetData()
    this.setData({ navItems: data.navItems, budgets: data.budgets })
  },
})
