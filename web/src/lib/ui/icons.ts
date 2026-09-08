export type IconName =
  | 'home'
  | 'calendar-clock'
  | 'check-square'
  | 'note'
  | 'dots'
  | 'plus'
  | 'x'
  | 'chevron-left'
  | 'chevron-right'
  | 'check'
  | 'check-circle'
  | 'trash'
  | 'clock'
  | 'pencil'
  | 'copy'
  | 'sun'
  | 'moon'
  | 'alarm'
  | 'chart'
  | 'shield'
  | 'logout'
  | 'search'
  | 'refresh'
  | 'target'
  | 'flame'
  | 'lock'
  | 'bell'
  | 'play'
  | 'pause'
  | 'tag'


export const ICON_PATHS: Record<IconName, string> = {
  home: '<path d="M4 11.5 12 4l8 7.5"/><path d="M6 10v9h12v-9"/><path d="M10 19v-5h4v5"/>',
  'calendar-clock':
    '<rect x="4" y="5.5" width="16" height="15" rx="2.5"/><path d="M4 10h16"/><path d="M8.5 3.5v3.5M15.5 3.5v3.5"/><circle cx="13.5" cy="14.5" r="3"/><path d="M13.5 13v1.6l1.2.8"/>',
  'check-square':
    '<rect x="4.5" y="4.5" width="15" height="15" rx="3"/><path d="m8.5 12.2 2.4 2.5 4.6-5"/>',
  note: '<path d="M6 4h9l3 3v13H6z"/><path d="M15 4v3.5h3.5"/><path d="M9 12h6M9 15.5h6"/>',
  dots: '<circle cx="5.5" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="18.5" cy="12" r="1.4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  'chevron-left': '<path d="m14.5 5.5-6.5 6.5 6.5 6.5"/>',
  'chevron-right': '<path d="m9.5 5.5 6.5 6.5-6.5 6.5"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  'check-circle': '<circle cx="12" cy="12" r="8"/><path d="m8.7 12.2 2.3 2.4 4.5-5"/>',
  trash: '<path d="M5 7h14"/><path d="M9.5 7V4.5h5V7"/><path d="M7 7l.8 12.5h8.4L17 7"/><path d="M10.2 10.5v5.5M13.8 10.5v5.5"/>',
  clock: '<circle cx="12" cy="12" r="8"/><path d="M12 7.5V12l3 2"/>',
  pencil: '<path d="m5 19 .8-3.4L16.5 5a1.8 1.8 0 0 1 2.6 0l-.1-.1a1.8 1.8 0 0 1 0 2.6L8.4 18.2 5 19z"/>',
  copy: '<rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M5.5 15.5h-1v-11h11v1"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4"/>',
  moon: '<path d="M20 13.5A8 8 0 0 1 10.5 4 8 8 0 1 0 20 13.5z"/>',
  alarm: '<circle cx="12" cy="13" r="7.5"/><path d="M12 9.5V13l2.5 1.7"/><path d="M5 3.5 7 6M19 3.5 17 6"/>',
  chart: '<path d="M5 20V12M10.5 20V5.5M16 20v-9M20 20H4"/>',
  shield: '<path d="M12 3.5 5 6v5.5c0 4.5 3 7.7 7 9 4-1.3 7-4.5 7-9V6z"/><path d="m9 11.8 2.2 2.2 4-4.3"/>',
  logout: '<path d="M14 8V5.5H5.5v13H14V16"/><path d="M10.5 12h9M17 8.5l2.5 3.5-2.5 3.5"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.4-5.7"/><path d="M20 3.5V8h-4.5"/>',
  target: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="4"/><circle cx="12" cy="12" r="1"/>',
  flame: '<path d="M12 3.5c.8 3 4.5 4.6 4.5 8.7a4.5 4.5 0 0 1-9 0c0-1.8.9-3 1.7-4 .3 1.1 1 1.8 1.9 2C10.8 7.5 11.5 5.5 12 3.5z"/>',
  lock: '<rect x="5.5" y="10.5" width="13" height="9.5" rx="2"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/>',
  bell: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2.5h-15z"/><path d="M10 21a2.2 2.2 0 0 0 4 0"/>',
  play: '<path d="M8 5.5v13l10-6.5z"/>',
  pause: '<path d="M8.5 5.5v13M15.5 5.5v13"/>',
  tag: '<path d="m4 12 8-8h7.5v7.5l-8 8z"/><circle cx="16" cy="8" r="1.3"/>',
}
