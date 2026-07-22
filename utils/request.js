const API_BASE_URL = 'http://127.0.0.1:8081'

let refreshTask = null

function createResponseError(response) {
  const body = response.data || {}
  const error = new Error(body.message || `请求失败（${response.statusCode}）`)
  error.code = body.code || 'NETWORK_REQUEST_FAILED'
  error.statusCode = response.statusCode
  return error
}

function send(options, token) {
  const headers = Object.assign({ 'content-type': 'application/json' }, options.header || {})
  if (token) headers.Authorization = `Bearer ${token}`
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${API_BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: headers,
      timeout: 10000,
      success: resolve,
      fail(error) {
        const requestError = new Error(error.errMsg || '无法连接到服务器')
        requestError.code = 'NETWORK_UNAVAILABLE'
        reject(requestError)
      },
    })
  })
}

function refreshSession(app) {
  if (refreshTask) return refreshTask
  const refreshToken = app.dataStore.getRefreshToken()
  if (!refreshToken) return Promise.reject(new Error('登录状态已失效，请重新登录'))
  refreshTask = send({
    url: '/api/v1/auth/refresh',
    method: 'POST',
    data: { refreshToken },
  }).then((response) => {
    const body = response.data || {}
    if (response.statusCode < 200 || response.statusCode >= 300 || body.code !== '0') {
      throw createResponseError(response)
    }
    app.dataStore.saveTokens(body.data)
    return body.data
  })
  refreshTask = refreshTask.then((result) => {
    refreshTask = null
    return result
  }, (error) => {
    refreshTask = null
    throw error
  })
  return refreshTask
}

function execute(app, options, retried) {
  const token = app.dataStore.getAccessToken()
  return send(options, token).then((response) => {
    const body = response.data || {}
    if (response.statusCode >= 200 && response.statusCode < 300 && body.code === '0') {
      return body.data
    }
    if (response.statusCode === 401 && !retried) {
      if (token && token !== app.dataStore.getAccessToken()) {
        return execute(app, options, true)
      }
      return refreshSession(app).then(
        () => execute(app, options, true),
        (error) => {
          app.dataStore.clearAuthSession()
          throw error
        }
      )
    }
    if (response.statusCode === 401) app.dataStore.clearAuthSession()
    throw createResponseError(response)
  })
}

function request(options) {
  return execute(getApp(), options, false)
}

module.exports = { API_BASE_URL, request }
