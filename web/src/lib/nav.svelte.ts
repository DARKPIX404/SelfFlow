/**
 * Простая стековая навигация по хэшу. Формат: '#/tab/seg/seg...'
 * Первый сегмент — вкладка, остальные — стек внутри неё.
 */

export interface Route {
  tab: string
  stack: string[]
}

export const TABS = ['today', 'routines', 'tasks', 'notes', 'more'] as const

function parse(hash: string): Route {
  const parts = hash.replace(/^#\/?/, '').split('/').filter(Boolean)
  const tab = parts[0] && (TABS as readonly string[]).includes(parts[0]) ? parts[0] : 'today'
  return { tab, stack: parts.slice(1) }
}

export const route = $state<Route>(parse(window.location.hash))

window.addEventListener('hashchange', () => {
  const next = parse(window.location.hash)
  route.tab = next.tab
  route.stack = next.stack
})

export function navigate(tab: string, stack: string[] = []): void {
  const target = '#/' + [tab, ...stack].join('/')
  if (window.location.hash === target) return
  window.location.hash = target
}

export function push(segments: string[]): void {
  navigate(route.tab, [...route.stack, ...segments])
}

export function pop(): void {
  navigate(route.tab, route.stack.slice(0, -1))
}

export function replaceStack(stack: string[]): void {
  navigate(route.tab, stack)
}
