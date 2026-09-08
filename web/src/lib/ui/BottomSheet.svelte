<script lang="ts">
  import type { Snippet } from 'svelte'

  interface Props {
    open: boolean
    onclose: () => void
    title?: string
    children: Snippet
    /** высота контента: обычный или растянутый */
    tall?: boolean
  }

  let { open, onclose, title, children, tall = false }: Props = $props()

  let dragging = $state(false)
  let dragY = $state(0)
  let contentEl = $state<HTMLDivElement | null>(null)

  function close() {
    onclose()
  }

  function onPointerDown(e: PointerEvent) {
    dragging = true
    dragY = 0
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  function onPointerMove(e: PointerEvent) {
    if (!dragging) return
    dragY = Math.max(0, e.clientY - startClientY)
  }

  let startClientY = 0

  function onHandleDown(e: PointerEvent) {
    startClientY = e.clientY
    onPointerDown(e)
  }

  function onPointerUp() {
    if (!dragging) return
    dragging = false
    if (dragY > 90) close()
    dragY = 0
  }

  function onBackdropClick() {
    close()
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') close()
  }
</script>

<svelte:window onkeydown={open ? onKeydown : undefined} />

{#if open}
  <div
    class="backdrop"
    class:dragging
    role="presentation"
    onclick={onBackdropClick}
    ontouchmove={(e) => e.preventDefault()}
  ></div>
  <div
    class="sheet"
    class:tall
    role="dialog"
    aria-modal="true"
    aria-label={title}
    style="transform: translateY({dragY}px); transition: {dragging ? 'none' : ''} transform 280ms cubic-bezier(.2,.8,.2,1), opacity 200ms"
  >
    <div
      class="handle-zone"
      role="presentation"
      onpointerdown={onHandleDown}
      onpointermove={onPointerMove}
      onpointerup={onPointerUp}
    >
      <div class="handle"></div>
    </div>
    {#if title}
      <header class="sheet-title">{title}</header>
    {/if}
    <div class="sheet-body" bind:this={contentEl}>
      {@render children()}
    </div>
  </div>
{/if}

<style>
  .backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    z-index: 90;
    animation: fadeIn 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .sheet {
    position: fixed;
    left: 50%;
    bottom: 0;
    width: 100%;
    max-width: 480px;
    translate: -50% 0;
    background: var(--surface);
    border-radius: 24px 24px 0 0;
    border: 1px solid var(--border);
    border-bottom: none;
    z-index: 100;
    display: flex;
    flex-direction: column;
    max-height: 85dvh;
    box-shadow: 0 -8px 40px rgba(0, 0, 0, 0.4);
    animation: rise 280ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }
  .handle-zone {
    padding: 10px 0 6px;
    cursor: grab;
    touch-action: none;
    flex-shrink: 0;
  }
  .handle {
    width: 40px;
    height: 4px;
    border-radius: 2px;
    background: var(--bg-deep);
    margin: 0 auto;
  }
  .sheet-title {
    font-size: 17px;
    font-weight: 600;
    padding: 4px 20px 10px;
    color: var(--text);
    flex-shrink: 0;
  }
  .sheet-body {
    overflow-y: auto;
    padding: 0 20px calc(20px + env(safe-area-inset-bottom, 0px));
    overscroll-behavior: contain;
  }
  .sheet.tall {
    max-height: 92dvh;
  }
  @keyframes rise {
    from { transform: translateY(100%); }
    to { transform: translateY(0); }
  }
  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @media (prefers-reduced-motion: reduce) {
    .sheet, .backdrop { animation: none; }
  }
</style>
