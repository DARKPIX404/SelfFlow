<script lang="ts">
  import { register } from '$lib/auth/session.svelte'
  import { syncAll } from '$lib/sync/sync'
  import AuthLayout from './AuthLayout.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import { navigate } from '$lib/nav.svelte'

  let email = $state('')
  let password = $state('')
  let confirm = $state('')
  let emailError = $state('')
  let passwordError = $state('')
  let error = $state('')
  let busy = $state(false)

  function humanize(e: unknown): string {
    const msg = e instanceof Error ? e.message : String(e)
    const lower = msg.toLowerCase()
    if (lower.includes('already') || lower.includes('uniqueness') || lower.includes('is not unique')) {
      return 'Этот email уже зарегистрирован'
    }
    if (lower.includes('password')) return 'Пароль слишком простой — минимум 8 символов'
    if (!navigator.onLine) return 'Нет соединения с сервером'
    return 'Не удалось зарегистрироваться. Попробуйте позже'
  }

  async function submit() {
    emailError = ''
    passwordError = ''
    error = ''
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      emailError = 'Введите корректный email'
      return
    }
    if (password.length < 8) {
      passwordError = 'Минимум 8 символов'
      return
    }
    if (password !== confirm) {
      passwordError = 'Пароли не совпадают'
      return
    }
    busy = true
    try {
      await register(email.trim(), password)
      navigate('today')
      void syncAll()
    } catch (e) {
      error = humanize(e)
    } finally {
      busy = false
    }
  }
</script>

<AuthLayout
  title="Создание аккаунта"
  submitLabel="Зарегистрироваться"
  {busy}
  {error}
  onsubmit={() => void submit()}
>
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
      placeholder="Минимум 8 символов"
      autocomplete="new-password"
      value={password}
      oninput={(v) => (password = v)}
    />
    <TextField
      label="Повторите пароль"
      type="password"
      placeholder="Ещё раз"
      autocomplete="new-password"
      value={confirm}
      oninput={(v) => (confirm = v)}
      error={passwordError}
    />
  {/snippet}

  <p class="switch">
    Уже есть аккаунт? <button type="button" class="link" onclick={() => navigate('login')}>Войти</button>
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
