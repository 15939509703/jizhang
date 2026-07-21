const { loginWithWechat } = require('../../utils/auth')

Page({
  data: {
    loading: false,
    demoLoading: false,
    userName: '',
    hasLocalUser: false,
  },

  onShow() {
    const store = getApp().dataStore
    const user = store.getStoredUser()
    this.setData({
      userName: user && user.nickName ? user.nickName : '',
      hasLocalUser: !!(user && user.loggedIn),
    })

    if (store.hasValidLocalSession()) {
      wx.reLaunch({ url: '/pages/home/home' })
    }
  },

  handleWechatLogin() {
    if (this.data.loading) {
      return
    }
    this.setData({ loading: true })
    loginWithWechat()
      .then((loginResult) => {
        const nextUser = getApp().dataStore.saveLoginSession(loginResult)
        getApp().globalData.userInfo = nextUser
        wx.reLaunch({ url: '/pages/home/home' })
      })
      .catch((error) => {
        wx.showToast({ title: error.message || '登录失败，请重试', icon: 'none', duration: 2500 })
      })
      .finally(() => this.setData({ loading: false }))
  },

  handleDemoLogin() {
    if (this.data.demoLoading) {
      return
    }
    this.setData({ demoLoading: true })
    const nextUser = { nickName: 'LHJ', avatarUrl: '', code: 'demo', loggedIn: true, loginAt: new Date().toISOString(), isMock: true }
    getApp().dataStore.saveUser(nextUser)
    getApp().globalData.userInfo = nextUser
    wx.reLaunch({ url: '/pages/home/home' })
  },
})
