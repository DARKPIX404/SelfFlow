<script lang="ts">
  import { getDb } from '$lib/db'
  import { notes, noteTags } from '$lib/db/repositories'
  import { session } from '$lib/auth/session.svelte'
  import type { Note, NoteTag } from '$lib/types'
  import { fmtDateShort } from '$lib/format'
  import AppBar from '$lib/ui/AppBar.svelte'
  import Chip from '$lib/ui/Chip.svelte'
  import EmptyState from '$lib/ui/EmptyState.svelte'
  import Fab from '$lib/ui/Fab.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import SearchBar from '$lib/ui/SearchBar.svelte'
  import { push } from '$lib/nav.svelte'
  import { haptic } from '$lib/ui/haptics'

  function reload(): Note[] {
    const user = session.user
    return user ? notes.list(user.id) : []
  }

  function tagsOf(noteId: string): string[] {
    return getDb()
      .query<NoteTag>('SELECT tag FROM note_tags WHERE note_id = ? AND deleted = 0 ORDER BY tag', [noteId])
      .map((t) => t.tag)
  }

  let query = $state('')

  let items = $derived.by(() => {
    void noteVersion
    const all = reload()
    const q = query.trim().toLowerCase()
    if (!q) return all
    return all.filter((n) => {
      if ((n.title ?? '').toLowerCase().includes(q) || n.content.toLowerCase().includes(q)) return true
      return tagsOf(n.id).some((t) => t.toLowerCase().includes(q))
    })
  })

  // реактивность на мутации/синк
  let noteVersion = $state(0)
  if (typeof window !== 'undefined') {
    window.addEventListener('selfflow:mutated', () => noteVersion++)
    window.addEventListener('selfflow:synced', () => noteVersion++)
  }

  function preview(n: Note): string {
    const text = n.content.replace(/\s+/g, ' ').trim()
    return text.length > 90 ? text.slice(0, 90) + '…' : text
  }

  function updatedShort(n: Note): string {
    return fmtDateShort(n.updated.slice(0, 10))
  }

  function createAndOpen() {
    const user = session.user
    if (!user) return
    haptic('light')
    const note = notes.create(user.id, { title: '', content: '' })
    noteVersion++
    push(['notes', note.id])
  }

  function open(n: Note) {
    haptic('light')
    push(['notes', n.id])
  }
</script>

<div class="screen">
  <AppBar title="Заметки" large />
  <div class="screen-body">
    <SearchBar value={query} oninput={(v) => (query = v)} placeholder="Поиск по заметкам и тегам" />
    {#if items.length === 0}
      <EmptyState
        label={query ? 'Ничего не найдено' : 'Заметок пока нет'}
        cta={query ? undefined : 'Новая заметка'}
        onCta={query ? undefined : createAndOpen}
      />
    {:else}
      <div class="stack">
        {#each items as n (n.id)}
          <ListItem title={n.title?.trim() || 'Без названия'} subtitle={preview(n)} time={updatedShort(n)} onclick={() => open(n)}>
            {#snippet trailing()}
              {@const tags = tagsOf(n.id)}
              {#if tags.length}
                <span class="tag-chips">
                  {#each tags.slice(0, 3) as tag (tag)}
                    <span class="tag-chip">{tag}</span>
                  {/each}
                  {#if tags.length > 3}
                    <span class="tag-chip">+{tags.length - 3}</span>
                  {/if}
                </span>
              {/if}
            {/snippet}
          </ListItem>
        {/each}
      </div>
    {/if}
  </div>
  <Fab onclick={createAndOpen} />
</div>

<style>
  .tag-chips {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
  .tag-chip {
    font-size: 11px;
    font-weight: 500;
    color: var(--accent);
    background: var(--accent-soft);
    border-radius: 8px;
    padding: 3px 8px;
  }
</style>
