<script lang="ts">
  import { Capacitor } from '@capacitor/core'
  import { login, enterGuestMode } from '$lib/auth/session.svelte'
  import { syncAll } from '$lib/sync/sync'
  import AuthLayout from './AuthLayout.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import { navigate } from '$lib/nav.svelte'

  const isNative = Capacitor.isNativePlatform()

  let email = $state('')
  let password = $state('')
  let emailError = $state('')
  let error = $state('')
  let busy = $state(false)

  /** Человеческий текст ошибок PocketBase */
  function humanize(e: unknown): string {
    const msg = e instanceof Error ? e.message : String(e)
    const lower = msg.toLowerCase()
    if (lower.includes('failed to authenticate') || lower.includes('invalid login')) {
      return 'Неверный email или пароль'
    }
    if (lower.includes('identity') || lower.includes('password')) return 'Неверный email или пароль'
    if (lower.includes('rate limit') || lower.includes('too many')) return 'Слишком много попыток, подождите минуту'
    if (!navigator.onLine) return 'Нет соединения с сервером'
    return 'Не удалось войти. Попробуйте позже'
  }

  async function submit() {
    emailError = ''
    error = ''
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      emailError = 'Введите корректный email'
      return
    }
    busy = true
    try {
      await login(email.trim(), password)
      navigate('today')
      void syncAll()
    } catch (e) {
      error = humanize(e)
    } finally {
      busy = false
    }
  }
</script>

<AuthLayout title="Вход в аккаунт" submitLabel="Войти" {busy} {error} onsubmit={() => void submit()}>
  {#snippet fields()}
    <TextField
      label="Email"
      type="email"
      placeholder="you@example.com"
      autocomplete="email"
      value={email}
      oninput={(v) => (email = v)}
      error={emailError}
      autofocus
    />
    <TextField
      label="Пароль"
      type="password"
      placeholder="••••••••"
      autocomplete="current-password"
      value={password}
      oninput={(v) => (password = v)}
    />
  {/snippet}

  <p class="switch">
    Нет аккаунта? <button type="button" class="link" onclick={() => navigate('register')}>Регистрация</button>
  </p>
  <div class="guest">
    <button type="button" class="guest-btn" onclick={enterGuestMode}>Продолжить без аккаунта</button>
    <p class="guest-hint">Данные останутся только на этом устройстве, без синхронизации</p>
  </div>
  {#if !isNative}
    <div class="apk">
      <a class="apk-btn" href="https://github.com/DARKPIX404/SelfFlow/releases/latest" target="_blank" rel="noopener">
        <Icon name="download" size={17} />
        Скачать приложение для Android (APK)
      </a>
      <p class="guest-hint">Устанавливается поверх — данные и вход сохраняются</p>
    </div>
  {/if}
</AuthLayout>

<style>
  .switch {
    text-align: center;
    font-size: 14px;
    color: var(--text-2);
  }
  .link {
    border: none;
    background: transparent;
    color: var(--accent);
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    padding: 4px;
  }
  .guest {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
  }
  .guest-btn {
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 14px;
    font-weight: 600;
    border-radius: 14px;
    padding: 12px 18px;
    min-height: 48px;
    cursor: pointer;
    width: 100%;
  }
  .guest-btn:active {
    filter: brightness(1.15);
  }
  .guest-hint {
    font-size: 12px;
    color: var(--text-muted);
    text-align: center;
  }
  .apk {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
  }
  .apk-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    border: 1px solid var(--accent);
    background: var(--accent-soft);
    color: var(--accent);
    font-size: 14px;
    font-weight: 600;
    border-radius: 14px;
    padding: 12px 18px;
    min-height: 48px;
    text-decoration: none;
  }
  .apk-btn:active {
    filter: brightness(1.15);
  }
</style>
