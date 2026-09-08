/** Форматирование дат/времени в русской локали */

const MONTHS_GEN = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
]

const MONTHS_NOM = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
]

const WEEKDAYS = ['воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота']

/** 'YYYY-MM-DD HH:mm...' → 'HH:mm' */
export function fmtTime(s: string | null | undefined): string {
  if (!s) return ''
  const t = s.slice(11, 16)
  return t || s.slice(0, 5)
}

/** 'YYYY-MM-DD' → '12 марта' */
export function fmtDateShort(s: string | null | undefined): string {
  if (!s) return ''
  const [y, m, d] = s.split('-').map(Number)
  if (!y || !m || !d) return s
  return `${d} ${MONTHS_GEN[m - 1]}`
}

/** 'YYYY-MM-DD' → 'понедельник, 8 сентября' */
export function fmtDateLong(s: string): string {
  const [y, m, d] = s.split('-').map(Number)
  const wd = WEEKDAYS[new Date(s + 'T12:00:00').getDay()]
  return `${wd}, ${d} ${MONTHS_GEN[m - 1]}`
}

export function greetingByHour(h: number): string {
  if (h < 5) return 'Доброй ночи'
  if (h < 12) return 'Доброе утро'
  if (h < 18) return 'Добрый день'
  return 'Добрый вечер'
}

export { MONTHS_NOM }
