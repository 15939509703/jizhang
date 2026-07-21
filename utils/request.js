const API_BASE_URL = 'http://127.0.0.1:8081'

function request(options) {
  const app = getApp()
  const token = app.dataStore.getAccessToken()
  const headers = Object.assign({ 'content-type': 'application/json' }, options.header || {})
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${API_BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: headers,
      timeout: 10000,
      success(response) {
        const body = response.data || {}
        if (response.statusCode >= 200 && response.statusCode < 300 && body.code === '0') {
          resolve(body.data)
          return
        }
        if (response.statusCode === 401) {
          app.dataStore.clearAuthSession()
        }
        const error = new Error(body.message || `请求失败（${response.statusCode}）`)
        error.code = body.code || 'NETWORK_REQUEST_FAILED'
        error.statusCode = response.statusCode
        reject(error)
      },
      fail(error) {
        const requestError = new Error(error.errMsg || '无法连接到服务器')
        requestError.code = 'NETWORK_UNAVAILABLE'
        reject(requestError)
      },
    })
  })
}

module.exports = { API_BASE_URL, request }
