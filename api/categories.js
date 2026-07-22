const { request } = require('../utils/request')

function getCategories(bookId, type) {
  const data = { bookId }
  if (type) {
    data.type = type
  }
  return request({
    url: '/api/v1/categories',
    method: 'GET',
    data,
  })
}

module.exports = { getCategories }
