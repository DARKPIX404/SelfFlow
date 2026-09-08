<script lang="ts">
  import { getDb } from '$lib/db'
  import { notes, noteTags } from '$lib/db/repositories'
  import { session } from '$lib/auth/session.svelte'
  import type { Note, NoteTag } from '$lib/types'
  import AppBar from '$lib/ui/AppBar.svelte'
  import Chip from '$lib/ui/Chip.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import { pop } from '$lib/nav.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'

  interface Props {
    noteId: string
  }

  let { noteId }: Props = $props()

  let note = $state<Note | null>(null)
  let title = $state('')
  let content = $state('')
  let tags = $state<string[]>([])
  let tagInput = $state('')
  let suggestions = $state<string[]>([])
  let savedFlash = $state(false)

  function load() {
    const n = notes.getRaw(noteId)
    if (!n || n.deleted) {
      note = null
      return
    }
    note = n
    title = n.title ?? ''
    content = n.content ?? ''
    tags = getDb()
      .query<NoteTag>('SELECT tag FROM note_tags WHERE note_id = ? AND deleted = 0 ORDER BY tag', [noteId])
      .map((t) => t.tag)
    tagInput = ''
    suggestions = []
  }

  load()
  if (typeof window !== 'undefined') {
    window.addEventListener('selfflow:synced', load)
  }

  function allTags(): string[] {
    const user = session.user
    if (!user) return []
    return [
      ...new Set(
        getDb()
          .query<{ tag: string }>('SELECT DISTINCT tag FROM note_tags WHERE owner = ? AND deleted = 0', [user.id])
          .map((r) => r.tag),
      ),
    ].sort()
  }

  // авто-сохранение с debounce
  let saveTimer: ReturnType<typeof setTimeout> | null = null

  function scheduleSave() {
    if (!note) return
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => {
      if (!note) return
      notes.update(note.id, { title: title.trim() || null, content })
      window.dispatchEvent(new CustomEvent('selfflow:mutated'))
      savedFlash = true
      setTimeout(() => (savedFlash = false), 1200)
    }, 600)
  }

  function onTagInput(v: string) {
    tagInput = v
    const q = v.trim().toLowerCase()
    const known = allTags().filter((t) => !tags.includes(t))
    suggestions = q ? known.filter((t) => t.toLowerCase().includes(q)).slice(0, 6) : []
  }

  function addTag(raw: string) {
    const tag = raw.trim().replace(/,/g, '')
    if (!tag || !note || tags.includes(tag)) return
    const user = session.user
    if (!user) return
    haptic('light')
    noteTags.create(user.id, { note_id: note.id, tag } as Partial<NoteTag>)
    tags = [...tags, tag].sort()
    tagInput = ''
    suggestions = []
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  function removeTag(tag: string) {
    if (!note) return
    haptic('light')
    const rows = getDb().query<NoteTag>(
      'SELECT id FROM note_tags WHERE note_id = ? AND tag = ? AND deleted = 0',
      [note.id, tag],
    )
    for (const r of rows) noteTags.remove(r.id)
    tags = tags.filter((t) => t !== tag)
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
  }

  function removeNote() {
    if (!note) return
    haptic('medium')
    const rows = getDb().query<NoteTag>('SELECT id FROM note_tags WHERE note_id = ? AND deleted = 0', [note.id])
    for (const r of rows) noteTags.remove(r.id)
    notes.remove(note.id)
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
    showToast({ message: 'Заметка удалена' })
    pop()
  }

  function onTagKeydown(e: KeyboardEvent) {
    if ((e.key === 'Enter' || e.key === ',') && tagInput.trim()) {
      e.preventDefault()
      addTag(tagInput)
    } else if (e.key === 'Backspace' && !tagInput && tags.length) {
      removeTag(tags[tags.length - 1])
    }
  }
</script>

<div class="screen editor">
  <AppBar title={title.trim() || 'Заметка'}>
    {#snippet actions()}
      {#if savedFlash}
        <span class="saved"><Icon name="check" size={16} /> Сохранено</span>
      {/if}
      <button type="button" class="icon-btn" aria-label="Удалить заметку" onclick={removeNote}>
        <Icon name="trash" size={20} />
      </button>
    {/snippet}
  </AppBar>

  {#if note}
    <div class="editor-body">
      <input
        class="title-input"
        placeholder="Заголовок"
        aria-label="Заголовок"
        value={title}
        oninput={(e) => {
          title = (e.target as HTMLInputElement).value
          scheduleSave()
        }}
      />
      <textarea
        class="content-input"
        placeholder="Текст заметки…"
        aria-label="Текст заметки"
        value={content}
        oninput={(e) => {
          content = (e.target as HTMLTextAreaElement).value
          scheduleSave()
        }}
      ></textarea>

      <section class="tags-block">
        <span class="f-label">Теги</span>
        <div class="tag-editor">
          {#each tags as tag (tag)}
            <Chip selected label={tag + '  ✕'} onclick={() => removeTag(tag)} />
          {/each}
          <input
            class="tag-input"
            placeholder="Добавить тег"
            aria-label="Новый тег"
            value={tagInput}
            oninput={(e) => onTagInput((e.target as HTMLInputElement).value)}
            onkeydown={onTagKeydown}
          />
        </div>
        {#if suggestions.length}
          <div class="suggestions">
            {#each suggestions as s (s)}
              <button type="button" class="suggestion" onclick={() => addTag(s)}>
                <Icon name="tag" size={14} /> {s}
              </button>
            {/each}
          </div>
        {/if}
      </section>
    </div>
  {:else}
    <div class="missing">
      <p>Заметка не найдена</p>
      <button type="button" class="link-btn" onclick={pop}>Назад</button>
    </div>
  {/if}
</div>

<style>
  .editor-body {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 4px 16px 24px;
    flex: 1;
  }
  .title-input {
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 22px;
    font-weight: 700;
    letter-spacing: -0.02em;
    outline: none;
    padding: 8px 0;
    font-family: inherit;
  }
  .title-input::placeholder,
  .content-input::placeholder,
  .tag-input::placeholder {
    color: var(--text-muted);
  }
  .content-input {
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 15px;
    line-height: 1.55;
    outline: none;
    min-height: 40dvh;
    resize: none;
    font-family: inherit;
    padding: 0;
  }
  .tags-block {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: auto;
    padding-top: 16px;
    border-top: 1px solid var(--border);
  }
  .f-label {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-2);
  }
  .tag-editor {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }
  .tag-input {
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 14px;
    outline: none;
    min-height: 36px;
    min-width: 120px;
    flex: 1;
    font-family: inherit;
  }
  .suggestions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .suggestion {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    font-size: 13px;
    border-radius: 10px;
    padding: 7px 10px;
    cursor: pointer;
  }
  .saved {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 500;
    color: var(--success);
    animation: fadeIn 200ms var(--ease);
  }
  .missing {
    padding: 40px 16px;
    text-align: center;
    color: var(--text-muted);
  }
  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }
</style>
