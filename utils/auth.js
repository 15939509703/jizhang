const { wechatLogin } = require('../api/auth')

function getWechatProfile() {
  return new Promise((resolve, reject) => {
    wx.getUserProfile({
      desc: '用于创建和同步记账账号',
      success: resolve,
      fail: reject,
    })
  })
}

function getWechatCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(result) {
        if (result.code) {
          resolve(result.code)
          return
        }
        reject(new Error('未获取到微信登录凭证'))
      },
      fail: reject,
    })
  })
}

async function loginWithWechat() {
  const profile = await getWechatProfile()
  const code = await getWechatCode()
  return wechatLogin({
    code,
    nickName: profile.userInfo.nickName || '微信用户',
    avatarUrl: profile.userInfo.avatarUrl || '',
  })
}

module.exports = { loginWithWechat }
