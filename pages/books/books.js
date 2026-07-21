Page({
  data: { navItems: [], books: [] },

  onShow() {
    const store = getApp().dataStore
    if (!store.ensureLoggedIn()) return
    const data = store.loadBooksData()
    this.setData({ navItems: data.navItems, books: data.books })
  },
})
