const ACCOUNT_TYPE_LABELS = {
  ALIPAY: '支付宝',
  BANK: '银行卡',
  CASH: '现金',
  CREDIT: '信用卡',
  WECHAT: '微信',
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatCurrency(value, currencyCode) {
  const symbols = { CNY: '¥', EUR: '€', GBP: '£', JPY: '¥', USD: '$' }
  const amount = Number(value || 0)
  const normalizedCurrencyCode = currencyCode || 'CNY'
  const sign = amount < 0 ? '-' : ''
  const symbol = symbols[normalizedCurrencyCode] || `${normalizedCurrencyCode} `
  return `${sign}${symbol}${Math.abs(amount).toFixed(2)}`
}

function formatSignedCurrency(value, currencyCode) {
  const amount = Number(value || 0)
  const sign = amount >= 0 ? '+' : '-'
  return `${sign}${formatCurrency(Math.abs(amount), currencyCode)}`
}

function formatDateTime(value) {
  const date = new Date(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDayLabel(value) {
  const date = new Date(value)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function findEntry(transaction, positive) {
  const entries = transaction.entries || []
  return entries.find((entry) => positive ? Number(entry.signedAmount) > 0 : Number(entry.signedAmount) < 0)
    || entries[0]
}

function mapTransaction(transaction, categoryMap, accountMap) {
  const isIncome = transaction.transactionType === 'INCOME'
  const sourceEntry = findEntry(transaction, false)
  const targetEntry = transaction.transactionType === 'TRANSFER' ? findEntry(transaction, true) : null
  const accountName = sourceEntry ? accountMap[sourceEntry.accountId] : ''
  const targetAccountName = targetEntry ? accountMap[targetEntry.accountId] : ''
  const signedAmount = isIncome ? Number(transaction.amount) : -Number(transaction.amount)
  return Object.assign({}, transaction, {
    account: targetAccountName ? `${accountName} -> ${targetAccountName}` : accountName || '未命名账户',
    amountClass: isIncome ? 'success' : transaction.transactionType === 'TRANSFER' ? 'primary' : 'danger',
    amountSignedText: transaction.transactionType === 'TRANSFER'
      ? formatCurrency(transaction.amount, transaction.currencyCode)
      : formatSignedCurrency(signedAmount, transaction.currencyCode),
    category: transaction.transactionType === 'TRANSFER'
      ? '转账'
      : categoryMap[transaction.categoryId] || '未分类',
    dateText: formatDateTime(transaction.happenedAt),
    dayLabel: formatDayLabel(transaction.happenedAt),
    type: transaction.transactionType.toLowerCase(),
  })
}

function groupTransactions(items) {
  const groups = []
  const groupMap = {}
  items.forEach((item) => {
    if (!groupMap[item.dayLabel]) {
      groupMap[item.dayLabel] = { dateLabel: item.dayLabel, expense: 0, income: 0, items: [] }
      groups.push(groupMap[item.dayLabel])
    }
    const group = groupMap[item.dayLabel]
    group.items.push(item)
    if (item.transactionType === 'EXPENSE') {
      group.expense += Number(item.amount)
    } else if (item.transactionType === 'INCOME') {
      group.income += Number(item.amount)
    }
  })
  return groups.map((group) => Object.assign({}, group, {
    totalText: `收 ${formatCurrency(group.income)} / 支 ${formatCurrency(group.expense)}`,
  }))
}

function accountTypeLabel(type) {
  return ACCOUNT_TYPE_LABELS[type] || type || '其他'
}

module.exports = {
  accountTypeLabel,
  formatCurrency,
  formatDateTime,
  formatSignedCurrency,
  groupTransactions,
  mapTransaction,
}
