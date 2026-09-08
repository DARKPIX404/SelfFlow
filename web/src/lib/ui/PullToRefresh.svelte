<script lang="ts" generics="T">
  import type { Snippet } from 'svelte'

  interface Props {
    onRefresh: () => Promise<void> | void
    children: Snippet
  }

  let { onRefresh, children }: Props = $props()

  let startY = $state<number | null>(null)
  let pull = $state(0)
  let refreshing = $state(false)
  const THRESHOLD = 72

  function onTouchStart(e: TouchEvent) {
    if (window.scrollY > 0 || refreshing) return
    startY = e.touches[0].clientY
  }

  function onTouchMove(e: TouchEvent) {
    if (startY === null) return
    const dy = e.touches[0].clientY - startY
    pull = Math.max(0, Math.min(dy * 0.5, 110))
  }

  async function onTouchEnd() {
    if (pull >= THRESHOLD && !refreshing) {
      refreshing = true
      try {
        await onRefresh()
      } finally {
        refreshing = false
      }
    }
    pull = 0
    startY = null
  }

  async function onMouseDown(e: MouseEvent) {
    if (window.scrollY > 0 || refreshing || e.button !== 0) return
    const y0 = e.clientY
    const move = (ev: MouseEvent) => {
      pull = Math.max(0, Math.min((ev.clientY - y0) * 0.5, 110))
    }
    const up = async () => {
      window.removeEventListener('mousemove', move)
      window.removeEventListener('mouseup', up)
      if (pull >= THRESHOLD && !refreshing) {
        refreshing = true
        try {
          await onRefresh()
        } finally {
          refreshing = false
        }
      }
      pull = 0
    }
    window.addEventListener('mousemove', move)
    window.addEventListener('mouseup', up)
  }

  let progress = $derived(Math.min(pull / THRESHOLD, 1))
</script>

<div
  class="ptr"
  role="presentation"
  ontouchstart={onTouchStart}
  ontouchmove={onTouchMove}
  ontouchend={onTouchEnd}
  onmousedown={onMouseDown}
>
  <div class="indicator" class:visible={pull > 8 || refreshing} style="height: {pull}px">
    {#if refreshing}
      <span class="spinner"></span>
    {:else}
      <svg class="arrow" class:ready={pull >= THRESHOLD} viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="opacity: {0.3 + progress * 0.7}; transform: rotate({progress * 180}deg)">
        <path d="M12 4v14M6 12l6 6 6-6" />
      </svg>
    {/if}
  </div>
  {@render children()}
</div>

<style>
  .ptr {
    min-height: 100%;
  }
  .indicator {
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    transition: height 120ms cubic-bezier(0.2, 0.8, 0.2, 1);
    color: var(--text-2);
  }
  .arrow {
    transition: transform 80ms linear;
  }
  .arrow.ready {
    color: var(--accent);
  }
  .spinner {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 2px solid var(--surface-2);
    border-top-color: var(--accent);
    animation: spin 0.8s linear infinite;
  }
  @keyframes spin {
    to { transform: rotate(360deg); }
  }
</style>
