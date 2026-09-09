<script lang="ts">
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'
  import type { IconName } from './icons'
  import { haptic } from './haptics'

  /**
   * Свайп-строка как в Telegram: действия под контентом раскрываются
   * синхронно с жестом. Полное раскрытие вправо/влево = выполнение действия.
   */
  interface Props {
    children: Snippet
    right?: { label: string; icon: IconName; onTrigger: () => void }
    left?: { label: string; icon: IconName; onTrigger: () => void }
  }

  let { children, right, left }: Props = $props()

  let x = $state(0)
  let dragging = $state(false)
  let startX = 0
  let startY = 0
  let axis: 'x' | 'y' | null = null
  let captured = false
  let rowEl = $state<HTMLDivElement | null>(null)
  let rowW = $state(300)

  const FULL_RATIO = 0.45 // доля ширины для «полного» раскрытия

  let rightW = $derived(right ? Math.min(rowW * 0.32, 130) : 0)
  let leftW = $derived(left ? Math.min(rowW * 0.32, 130) : 0)

  function onPointerDown(e: PointerEvent) {
    if (e.button !== 0 && e.pointerType === 'mouse') return
    // клик по вложенным кнопкам (кебаб, чекбокс) не должен превращаться в
    // жест: без этого захват указателя при малейшем смещении пальца ретаргетит
    // click на строку, и кнопка перестаёт нажиматься на тач-устройствах
    if ((e.target as HTMLElement).closest('button, a, input, select, textarea, [data-swipe-ignore]')) return
    dragging = true
    axis = null
    captured = false
    startX = e.clientX
    startY = e.clientY
    rowW = rowEl?.getBoundingClientRect().width ?? 300
    // setPointerCapture НЕ делаем здесь: перехваченный указатель глушит
    // click на вложенных кнопках (CheckCircle). Захватываем лениво,
    // только когда жест подтвердился как горизонтальный.
  }

  function onPointerMove(e: PointerEvent) {
    if (!dragging) return
    const dx = e.clientX - startX
    const dy = e.clientY - startY
    if (!axis) {
      if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return
      axis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y'
      if (axis === 'y') return
      ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
      captured = true
    }
    if (axis !== 'x') return
    let nx = dx
    if (nx > 0 && !right) nx = Math.min(nx * 0.2, 24)
    if (nx < 0 && !left) nx = Math.max(nx * 0.2, -24)
    x = nx
  }

  function onPointerUp(e: PointerEvent) {
    if (!dragging) return
    dragging = false
    if (captured) {
      captured = false
      try {
        ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
      } catch {
        // указатель уже отпущен браузером
      }
    }
    if (axis !== 'x') {
      x = 0
      return
    }
    const thresholdRight = rightW * FULL_RATIO
    const thresholdLeft = leftW * FULL_RATIO
    if (right && x > thresholdRight && x >= rightW * 0.8) {
      // полное раскрытие вправо → действие
      x = rowW
      haptic('medium')
      setTimeout(() => {
        right.onTrigger()
        x = 0
      }, 140)
      return
    }
    if (left && x < -thresholdLeft && x <= -leftW * 0.8) {
      x = -rowW
      haptic('medium')
      setTimeout(() => {
        left.onTrigger()
        x = 0
      }, 140)
      return
    }
    x = 0
  }

  let rightProgress = $derived(right ? Math.min(Math.max(x / rightW, 0), 1) : 0)
  let leftProgress = $derived(left ? Math.min(Math.max(-x / leftW, 0), 1) : 0)
</script>

<div class="swipe" bind:this={rowEl}>
  {#if right}
    <div class="under right" style="width: {Math.max(x, 0)}px">
      <span class="u-icon" style="opacity: {Math.min(rightProgress * 2, 1)}; transform: scale({0.6 + rightProgress * 0.4})">
        <Icon name={right.icon} size={20} />
      </span>
    </div>
  {/if}
  {#if left}
    <div class="under left" style="width: {Math.max(-x, 0)}px">
      <span class="u-icon" style="opacity: {Math.min(leftProgress * 2, 1)}; transform: scale({0.6 + leftProgress * 0.4})">
        <Icon name={left.icon} size={20} />
      </span>
    </div>
  {/if}
  <div
    class="content"
    class:dragging
    role="presentation"
    onpointerdown={onPointerDown}
    onpointermove={onPointerMove}
    onpointerup={onPointerUp}
    onpointercancel={onPointerUp}
    style="transform: translateX({x}px)"
  >
    {@render children()}
  </div>
</div>

<style>
  .swipe {
    position: relative;
    overflow: hidden;
    border-radius: 16px;
  }
  .under {
    position: absolute;
    top: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    overflow: hidden;
  }
  .under.right {
    left: 0;
    justify-content: flex-start;
    padding-left: 20px;
    background: var(--success);
    color: var(--bg);
    border-radius: 16px 0 0 16px;
  }
  .under.left {
    right: 0;
    justify-content: flex-end;
    padding-right: 20px;
    background: var(--accent);
    color: var(--text);
    border-radius: 0 16px 16px 0;
  }
  .u-icon {
    display: inline-flex;
    flex-shrink: 0;
  }
  .content {
    position: relative;
    z-index: 1;
    background: var(--surface);
    transition: transform 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
    touch-action: pan-y;
    cursor: grab;
  }
  .content.dragging {
    transition: none;
    cursor: grabbing;
  }
</style>
