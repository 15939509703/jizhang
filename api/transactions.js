const { request } = require('../utils/request')

function getTransactionSummary(bookId) {
  return request({
    url: '/api/v1/transactions/summary',
    method: 'GET',
    data: { bookId },
  })
}

module.exports = { getTransactionSummary }
