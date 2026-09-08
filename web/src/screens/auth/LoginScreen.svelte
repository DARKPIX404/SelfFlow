<script lang="ts">
  import { login } from '$lib/auth/session.svelte'
  import { syncAll } from '$lib/sync/sync'
  import AuthLayout from './AuthLayout.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import { navigate } from '$lib/nav.svelte'

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
</style>
