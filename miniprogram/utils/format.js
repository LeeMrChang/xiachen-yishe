const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad2(value) {
  return value < 10 ? `0${value}` : `${value}`
}

function parseParts(value) {
  if (!value) {
    return null
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/.exec(String(value))
  if (!match) {
    return null
  }
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: match[4] === undefined ? null : Number(match[4]),
    minute: match[5] === undefined ? null : Number(match[5])
  }
}

function dateKey(value) {
  if (value instanceof Date) {
    return `${value.getFullYear()}-${pad2(value.getMonth() + 1)}-${pad2(value.getDate())}`
  }
  const parts = parseParts(value)
  return parts ? `${parts.year}-${pad2(parts.month)}-${pad2(parts.day)}` : ''
}

function timeText(value) {
  const raw = value ? String(value) : ''
  const clock = /^(\d{2}):(\d{2})/.exec(raw)
  if (clock) {
    return `${clock[1]}:${clock[2]}`
  }
  const parts = parseParts(value)
  return parts && parts.hour !== null ? `${pad2(parts.hour)}:${pad2(parts.minute)}` : ''
}

function weekday(year, month, day) {
  return WEEK[new Date(year, month - 1, day).getDay()]
}

function dateText(value) {
  const parts = parseParts(value)
  if (!parts) {
    return ''
  }
  return `${parts.month}月${parts.day}日 ${weekday(parts.year, parts.month, parts.day)}`
}

function dayLabel(key) {
  const today = dateKey(new Date())
  if (key === today) {
    return '今天'
  }
  if (key === dateKey(new Date(Date.now() - 24 * 3600 * 1000))) {
    return '昨天'
  }
  return dateText(key)
}

function visitLabel(date, time) {
  return `${dateText(date)} · ${timeText(time) || '时间待定'}`
}

function addDaysKey(days) {
  const now = new Date()
  return dateKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() + days))
}

function shortStoreName(name) {
  return name ? String(name).replace('夏晨衣舍 · ', '') : ''
}

function requestId() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

function formatPrice(cent) {
  const value = Number(cent || 0) / 100
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

module.exports = {
  pad2,
  parseParts,
  dateKey,
  timeText,
  dateText,
  dayLabel,
  visitLabel,
  addDaysKey,
  shortStoreName,
  requestId,
  formatPrice
}
