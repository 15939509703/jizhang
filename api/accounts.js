const { request } = require('../utils/request')

function getAccounts(bookId) {
  return request({
    url: '/api/v1/accounts',
    method: 'GET',
    data: { bookId },
  })
}

function createAccount(payload) {
  return request({
    url: '/api/v1/accounts',
    method: 'POST',
    data: payload,
  })
}

module.exports = { createAccount, getAccounts }
