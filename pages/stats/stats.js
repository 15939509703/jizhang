Page({
  data: { navItems: [], summary: { incomeText: '¥0.00', expenseText: '¥0.00', balanceText: '¥0.00' }, expenseSummary: [], trendSeries: [] },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    const data = store.loadStatsData()
    this.setData({ navItems: data.navItems, summary: data.summary, expenseSummary: data.expenseSummary, trendSeries: data.trendSeries })
  },
})
