Page({
  data: { navItems: [], groupedBills: [] },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    this.refresh()
  },

  refresh() {
    const data = getApp().dataStore.loadBillsData()
    this.setData({ navItems: data.navItems, groupedBills: data.groupedBills })
  },

  handleDelete(event) {
    getApp().dataStore.deleteBill(event.currentTarget.dataset.id)
    this.refresh()
  },
})
