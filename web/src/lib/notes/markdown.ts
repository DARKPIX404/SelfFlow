/**
 * Мини-markdown для заметок: своими силами, без зависимостей.
 * Вход всегда экранируется — в DOM уходит только безопасный HTML.
 *
 * Поддержка: **жирный**, *курсив*, ~~зачёркнутый~~, `код`, автоссылки
 * http(s)://, заголовки #/##/###, списки -/* и 1., цитаты >, абзацы.
 */

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** инлайн-разметка поверх уже экранированной строки */
function inline(raw: string): string {
  let s = escapeHtml(raw)
  // код — первым, чтобы внутри не работала остальная разметка
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>')
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/~~([^~]+)~~/g, '<s>$1</s>')
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
  s = s.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>')
  return s
}

/** markdown-текст → безопасный HTML */
export function renderMarkdown(src: string): string {
  const lines = src.replace(/\r\n?/g, '\n').split('\n')
  const out: string[] = []
  let list: { ordered: boolean; items: string[] } | null = null
  let para: string[] = []

  const flushPara = () => {
    if (para.length) {
      out.push('<p>' + para.map(inline).join('<br>') + '</p>')
      para = []
    }
  }
  const flushList = () => {
    if (list) {
      const tag = list.ordered ? 'ol' : 'ul'
      out.push(`<${tag}>` + list.items.map((i) => '<li>' + inline(i) + '</li>').join('') + `</${tag}>`)
      list = null
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()
    const heading = trimmed.match(/^(#{1,3})\s+(.*)$/)
    const bullet = trimmed.match(/^[-*]\s+(.*)$/)
    const ordered = trimmed.match(/^\d+[.)]\s+(.*)$/)
    if (!trimmed) {
      flushPara()
      flushList()
      continue
    }
    if (heading) {
      flushPara()
      flushList()
      const level = heading[1].length
      out.push(`<h${level + 1} class="md-h${level}">` + inline(heading[2]) + `</h${level + 1}>`)
    } else if (bullet || ordered) {
      flushPara()
      const orderedList = !!ordered
      if (!list || list.ordered !== orderedList) {
        flushList()
        list = { ordered: orderedList, items: [] }
      }
      list.items.push((bullet ?? ordered)![1])
    } else if (trimmed.startsWith('> ')) {
      flushPara()
      flushList()
      out.push('<blockquote>' + inline(trimmed.slice(2)) + '</blockquote>')
    } else {
      flushList()
      para.push(line)
    }
  }
  flushPara()
  flushList()
  return out.join('')
}

/** плоский текст для превью в списке — маркеры разметки убираем */
export function stripMarkdown(src: string): string {
  return src
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]*)\*\*/g, '$1')
    .replace(/~~([^~]*)~~/g, '$1')
    .replace(/(^|\s)#{1,3}\s+/g, '$1')
    .replace(/^\s*[-*]\s+/gm, '')
    .replace(/^\s*\d+[.)]\s+/gm, '')
    .replace(/^\s*>\s?/gm, '')
}
