<script lang="ts">
  import { haptic } from '$lib/ui/haptics'
  import { setSetting } from '$lib/settings.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import type { IconName } from '$lib/ui/icons'

  interface Slide {
    icon: IconName
    title: string
    text: string
  }

  const slides: Slide[] = [
    {
      icon: 'calendar-clock',
      title: 'Распорядок дня',
      text: 'Планируйте день по времени: подъём, работа, отдых. Напоминания придут вовремя, даже офлайн.',
    },
    {
      icon: 'check-square',
      title: 'Задачи и заметки',
      text: 'Ведите дела с приоритетами и сроками, а мысли сохраняйте в заметках с тегами — всё под рукой.',
    },
    {
      icon: 'shield',
      title: 'Приватность и синхронизация',
      text: 'Данные сначала живут на устройстве и синхронизируются с вашим сервером. Можно защитить вход PIN-кодом.',
    },
  ]

  let page = $state(0)

  function next() {
    haptic('selection')
    if (page < slides.length - 1) page++
    else finish()
  }

  function finish() {
    setSetting('onboarding_completed', '1')
    window.dispatchEvent(new CustomEvent('selfflow:mutated'))
    visible = false
  }

  function skip() {
    haptic('light')
    finish()
  }

  let visible = $state(true)
</script>

{#if visible}
  <div class="onboarding">
    <div class="skip-row">
      <button type="button" class="skip" onclick={skip}>Пропустить</button>
    </div>
    <div class="pager">
      {#each slides as slide, i (i)}
        <section class="slide" class:active={i === page}>
          <span class="art"><Icon name={slide.icon} size={72} strokeWidth={1.4} /></span>
          <h1>{slide.title}</h1>
          <p>{slide.text}</p>
        </section>
      {/each}
    </div>
    <div class="dots">
      {#each slides as _, i (i)}
        <span class="dot" class:active={i === page}></span>
      {/each}
    </div>
    <footer class="footer">
      <button type="button" class="btn" onclick={next}>
        {page < slides.length - 1 ? 'Далее' : 'Начать'}
      </button>
    </footer>
  </div>
{/if}

<style>
  .onboarding {
    position: fixed;
    inset: 0;
    z-index: 500;
    background: var(--bg);
    display: flex;
    flex-direction: column;
    max-width: 480px;
    margin: 0 auto;
    left: 0;
    right: 0;
  }
  .skip-row {
    display: flex;
    justify-content: flex-end;
    padding: 16px;
  }
  .skip {
    border: none;
    background: transparent;
    color: var(--text-muted);
    font-size: 14px;
    font-weight: 500;
    padding: 10px 12px;
    min-height: 44px;
    cursor: pointer;
  }
  .pager {
    flex: 1;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .slide {
    display: none;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    text-align: center;
    padding: 0 32px;
    animation: slideIn var(--dur-std) var(--ease);
  }
  .slide.active {
    display: flex;
  }
  .art {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 140px;
    height: 140px;
    border-radius: 36px;
    background: var(--accent-soft);
    color: var(--accent);
  }
  h1 {
    font-size: 24px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--text);
  }
  p {
    font-size: 15px;
    color: var(--text-2);
    max-width: 320px;
  }
  .dots {
    display: flex;
    justify-content: center;
    gap: 8px;
    padding: 24px 0;
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--surface-2);
    transition: background var(--dur-std) var(--ease);
  }
  .dot.active {
    background: var(--accent);
  }
  .footer {
    padding: 0 20px calc(28px + env(safe-area-inset-bottom, 0px));
  }
  .btn {
    width: 100%;
    border: none;
    background: var(--accent);
    color: var(--text);
    font-size: 16px;
    font-weight: 700;
    border-radius: 14px;
    padding: 15px;
    min-height: 52px;
    cursor: pointer;
    transition: filter var(--dur-micro);
  }
  .btn:active {
    filter: brightness(1.15);
  }
  @keyframes slideIn {
    from { opacity: 0; transform: translateX(24px); }
    to { opacity: 1; transform: translateX(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    .slide { animation: none; }
  }
</style>
