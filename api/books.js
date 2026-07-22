const { request } = require('../utils/request')

function getBooks() {
  return request({
    url: '/api/v1/books',
    method: 'GET',
  })
}

function createBook(payload) {
  return request({
    url: '/api/v1/books',
    method: 'POST',
    data: payload,
  })
}

module.exports = { createBook, getBooks }
