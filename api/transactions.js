const { request } = require('../utils/request')

function getTransactionSummary(bookId) {
  return request({
    url: '/api/v1/transactions/summary',
    method: 'GET',
    data: { bookId },
  })
}

function getTransactions(params) {
  return request({
    url: '/api/v1/transactions',
    method: 'GET',
    data: params,
  })
}

function createTransaction(payload) {
  return request({
    url: '/api/v1/transactions',
    method: 'POST',
    data: payload,
  })
}

function voidTransaction(transactionId) {
  return request({
    url: `/api/v1/transactions/${transactionId}/void`,
    method: 'POST',
  })
}

module.exports = {
  createTransaction,
  getTransactionSummary,
  getTransactions,
  voidTransaction,
}
