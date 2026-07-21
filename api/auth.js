const { request } = require('../utils/request')

function wechatLogin(params) {
  return request({
    url: '/api/v1/auth/wechat/login',
    method: 'POST',
    data: params,
  })
}

module.exports = { wechatLogin }
