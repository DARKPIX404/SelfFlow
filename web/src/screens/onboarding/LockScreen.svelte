<script lang="ts">
  import { onMount } from 'svelte'
  import { verifyPin, pinLock } from '$lib/settings.svelte'
  import { authenticateWithBiometrics, biometricsAvailable } from '$lib/auth/biometrics'
  import { haptic } from '$lib/ui/haptics'
  import Icon from '$lib/ui/Icon.svelte'

  let pin = $state('')
  let error = $state(false)
  let busy = $state(false)

  const MAX = 4

  // Натив: сначала пробуем биометрию (если включена в настройках),
  // PIN остаётся фолбэком
  onMount(() => {
    void (async () => {
      if (!pinLock.locked) return
      if (!(await biometricsAvailable())) return
      if (await authenticateWithBiometrics()) {
        haptic('medium')
        pinLock.locked = false
      }
    })()
  })

  async function press(digit: string) {
    if (pin.length >= MAX || busy) return
    haptic('light')
    error = false
    pin += digit
    if (pin.length === MAX) {
      busy = true
      const ok = await verifyPin(pin)
      busy = false
      if (ok) {
        haptic('medium')
        pinLock.locked = false
      } else {
        haptic('notification')
        error = true
        pin = ''
      }
    }
  }

  function backspace() {
    haptic('light')
    pin = pin.slice(0, -1)
    error = false
  }

  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫']
</script>

<div class="lock">
  <div class="head">
    <span class="lock-ic" class:error><Icon name="lock" size={30} /></span>
    <h1>Введите PIN-код</h1>
    {#if error}
      <p class="err">Неверный код, попробуйте ещё раз</p>
    {/if}
  </div>
  <div class="dots" class:error>
    {#each Array(MAX) as _, i (i)}
      <span class="dot" class:filled={i < pin.length}></span>
    {/each}
  </div>
  <div class="pad">
    {#each keys as key, i (i)}
      {#if key === ''}
        <span></span>
      {:else if key === '⌫'}
        <button type="button" class="key" aria-label="Стереть" onclick={backspace}>
          <Icon name="chevron-left" size={22} />
        </button>
      {:else}
        <button type="button" class="key" onclick={() => press(key)}>{key}</button>
      {/if}
    {/each}
  </div>
</div>

<style>
  .lock {
    position: fixed;
    inset: 0;
    z-index: 400;
    background: var(--bg);
    max-width: 480px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 60px 24px calc(32px + env(safe-area-inset-bottom, 0px));
    left: 0;
    right: 0;
  }
  .head {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
  }
  .lock-ic {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 72px;
    border-radius: 50%;
    background: var(--surface);
    color: var(--text-2);
  }
  .lock-ic.error {
    color: var(--accent);
    animation: shake 300ms var(--ease);
  }
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: var(--text);
  }
  .err {
    font-size: 13px;
    color: var(--accent);
  }
  .dots {
    display: flex;
    gap: 16px;
    margin: 36px 0 48px;
  }
  .dot {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    border: 1.5px solid var(--border);
    transition: background 120ms var(--ease), border-color 120ms var(--ease);
  }
  .dot.filled {
    background: var(--text);
    border-color: var(--text);
  }
  .dots.error .dot {
    border-color: var(--accent);
  }
  .dots.error .dot.filled {
    background: var(--accent);
  }
  .pad {
    display: grid;
    grid-template-columns: repeat(3, 76px);
    gap: 18px;
    margin-top: auto;
  }
  .key {
    width: 76px;
    height: 76px;
    border-radius: 50%;
    border: none;
    background: var(--surface);
    color: var(--text);
    font-size: 26px;
    font-weight: 500;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background var(--dur-micro);
    font-variant-numeric: tabular-nums;
  }
  .key:active {
    background: var(--surface-2);
  }
  @keyframes shake {
    0%, 100% { transform: translateX(0); }
    25% { transform: translateX(-6px); }
    75% { transform: translateX(6px); }
  }
</style>
