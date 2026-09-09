<script lang="ts">
  import { session, logout, GUEST_OWNER } from '$lib/auth/session.svelte'
  import { syncStatus } from '$lib/sync/status.svelte'
  import { syncAll } from '$lib/sync/sync'
  import {
    theme,
    applyTheme,
    getSetting,
    setSetting,
    pinEnabled,
    setPin,
    verifyPin,
    clearPin,
    alarmRingtoneOptions,
    notificationSoundOptions,
    playRingtone,
    alarmSoundKey,
    notificationSoundKey,
  } from '$lib/settings.svelte'
  import { rescheduleAlarms, requestRescheduleReminders } from '$lib/notifications'
  import { exportBackup, parseBackup, applyBackup, BackupError } from '$lib/backup'
  import { getDb } from '$lib/db'
  import AppBar from '$lib/ui/AppBar.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import ListItem from '$lib/ui/ListItem.svelte'
  import Toggle from '$lib/ui/Toggle.svelte'
  import TextField from '$lib/ui/TextField.svelte'
  import Dialog from '$lib/ui/Dialog.svelte'
  import { showToast } from '$lib/ui/toast.svelte'
  import { haptic } from '$lib/ui/haptics'
  import { Updater } from '$lib/native/updater'
  import { updateState, checkForUpdate } from '$lib/update.svelte'

  // --- аккаунт ---
  const isGuest = $derived(session.user?.id === GUEST_OWNER)

  function doSync() {
    haptic('light')
    void syncAll()
  }

  function doLogout() {
    haptic('medium')
    void logout()
  }

  // --- тема ---
  function toggleTheme(v: boolean) {
    haptic('selection')
    applyTheme(v ? 'light' : 'dark')
  }

  // --- звуки: будильник и уведомления — отдельные настройки ---
  let alarmRingtone = $state(alarmSoundKey())
  let notificationSound = $state(notificationSoundKey())
  let playing = $state<string | null>(null)

  function pickAlarmSound(key: string) {
    haptic('light')
    alarmRingtone = key
    setSetting('alarm_sound', key)
    playPreview(key)
    // перепланировать будильники/дайджест с новым звуком
    void rescheduleAlarms()
    requestRescheduleReminders()
  }

  function pickNotificationSound(key: string) {
    haptic('light')
    notificationSound = key
    setSetting('notification_sound', key)
    playPreview(key)
    requestRescheduleReminders()
  }

  function playPreview(key: string) {
    playing = key
    playRingtone(key)
    setTimeout(() => {
      if (playing === key) playing = null
    }, 8000)
  }

  // --- PIN ---
  let biometricOn = $state(getSetting('biometric_enabled') === '1')

  function toggleBiometric(v: boolean) {
    haptic('light')
    biometricOn = v
    setSetting('biometric_enabled', v ? '1' : '0')
  }

  let pinDialogOpen = $state(false)
  let pinMode = $state<'setup' | 'change-disable'>('setup')
  let pinStep = $state<'enter' | 'confirm'>('enter')
  let pinFirst = $state('')
  let pinValue = $state('')
  let pinError = $state('')

  function openPinDialog() {
    if (pinEnabled()) {
      pinMode = 'change-disable'
      pinStep = 'enter'
      pinValue = ''
      pinError = ''
      pinDialogOpen = true
    } else {
      pinMode = 'setup'
      pinStep = 'enter'
      pinFirst = ''
      pinValue = ''
      pinError = ''
      pinDialogOpen = true
    }
  }

  async function submitPin() {
    if (pinValue.length < 4) {
      pinError = 'Минимум 4 цифры'
      return
    }
    if (pinMode === 'setup') {
      if (pinStep === 'enter') {
        pinFirst = pinValue
        pinStep = 'confirm'
        pinValue = ''
        pinError = ''
        return
      }
      if (pinValue !== pinFirst) {
        pinError = 'Коды не совпадают'
        pinStep = 'enter'
        pinValue = ''
        return
      }
      await setPin(pinValue)
      pinDialogOpen = false
      haptic('medium')
      showToast({ message: 'PIN-код установлен' })
      return
    }
    // change-disable: сначала проверяем текущий
    const ok = await verifyPin(pinValue)
    if (!ok) {
      pinError = 'Неверный код'
      pinValue = ''
      return
    }
    clearPin()
    pinDialogOpen = false
    haptic('medium')
    showToast({ message: 'PIN-код отключён' })
  }

  function onPinInput(v: string) {
    pinValue = v.replace(/\D/g, '').slice(0, 8)
    pinError = ''
  }

  // --- обновление ---
  let appVersion = $state('')

  $effect(() => {
    void Updater.getAppInfo().then((info) => {
      appVersion = info.versionName
      if (!updateState.currentVersion) updateState.currentVersion = info.versionName
    })
  })

  function doCheckUpdate() {
    haptic('light')
    void checkForUpdate(true)
  }

  // --- бэкап ---
  let dataBusy = $state(false)
  let importConfirmOpen = $state(false)
  let fileInput = $state<HTMLInputElement | undefined>(undefined)

  function errMessage(e: unknown): string {
    if (e instanceof BackupError) return e.message
    if (e instanceof Error) return e.message
    return String(e)
  }

  async function doExport() {
    if (dataBusy || !session.user) return
    dataBusy = true
    haptic('light')
    try {
      await exportBackup(session.user.id)
      showToast({ message: 'Бэкап экспортирован' })
    } catch (e) {
      showToast({ message: `Ошибка экспорта: ${errMessage(e)}` })
    } finally {
      dataBusy = false
    }
  }

  function confirmImport() {
    importConfirmOpen = false
    fileInput?.click()
  }

  async function onBackupFilePicked(e: Event) {
    const input = e.currentTarget as HTMLInputElement
    const file = input.files?.[0]
    input.value = '' // повторный выбор того же файла должен снова дать событие
    if (!file || !session.user || dataBusy) return
    dataBusy = true
    try {
      const text = await file.text()
      const parsed = parseBackup(text)
      const stats = applyBackup(getDb(), parsed, session.user.id)
      // расписание уведомлений/будильников строится по данным БД — перепланируем
      void rescheduleAlarms()
      requestRescheduleReminders()
      void syncAll()
      haptic('medium')
      showToast({ message: `Импортировано записей: ${stats.imported}` })
    } catch (e) {
      showToast({ message: `Импорт не выполнен: ${errMessage(e)}` })
    } finally {
      dataBusy = false
    }
  }
</script>

<div class="screen">
  <AppBar title="Настройки" large />
  <div class="screen-body">
    <!-- Аккаунт -->
    <h2 class="section-title">Аккаунт</h2>
    <section class="card account">
      <div class="account-head">
        <span class="avatar"><Icon name="shield" size={20} /></span>
        <div class="account-texts">
          <h3>{isGuest ? 'Локальный режим' : (session.user?.email ?? '—')}</h3>
          <p class="sync-line">
            {#if isGuest}
              Без аккаунта — синхронизация отключена
            {:else if syncStatus.running}
              Синхронизация…
            {:else if syncStatus.lastError}
              Ошибка синка
            {:else}
              Синхронизировано
            {/if}
          </p>
        </div>
        {#if !isGuest && syncStatus.pending > 0}
          <span class="queue-badge" title="Неотправленные изменения">{syncStatus.pending}</span>
        {/if}
      </div>
      <div class="account-actions">
        {#if isGuest}
          <button type="button" class="pill-btn" onclick={doLogout}>
            <Icon name="logout" size={16} /> Войти в аккаунт
          </button>
        {:else}
          <button type="button" class="pill-btn" onclick={doSync} disabled={syncStatus.running}>
            <Icon name="refresh" size={16} /> Синхронизировать
          </button>
          <button type="button" class="pill-btn danger" onclick={doLogout}>
            <Icon name="logout" size={16} /> Выйти
          </button>
        {/if}
      </div>
    </section>

    <!-- Тема -->
    <h2 class="section-title">Тема</h2>
    <section class="card row">
      <div class="row-texts">
        <h3>Светлая тема</h3>
        <p>Пергамент вместо чернил</p>
      </div>
      <Toggle checked={theme.value === 'light'} onchange={toggleTheme} ariaLabel="Светлая тема" />
    </section>

    <!-- Звуки -->
    <h2 class="section-title">Звуки</h2>
    <section class="card">
      <h3 class="card-label">Звук будильника</h3>
      <p class="card-sub">Будильник подъёма/отбоя поверх экрана</p>
      <div class="ring-list">
        {#each alarmRingtoneOptions as opt (opt.key)}
          <button type="button" class="ring-opt" class:selected={alarmRingtone === opt.key} onclick={() => pickAlarmSound(opt.key)}>
            <span>{opt.label}</span>
            {#if playing === opt.key}
              <span class="eq"><i></i><i></i><i></i></span>
            {:else if opt.key !== 'system'}
              <Icon name="play" size={15} />
            {/if}
          </button>
        {/each}
      </div>
    </section>
    <section class="card">
      <h3 class="card-label">Звук уведомлений</h3>
      <p class="card-sub">Рутины, привычки и задачи</p>
      <div class="ring-list">
        {#each notificationSoundOptions as opt (opt.key)}
          <button type="button" class="ring-opt" class:selected={notificationSound === opt.key} onclick={() => pickNotificationSound(opt.key)}>
            <span>{opt.label}</span>
            {#if playing === opt.key}
              <span class="eq"><i></i><i></i><i></i></span>
            {:else if opt.key !== 'system'}
              <Icon name="play" size={15} />
            {/if}
          </button>
        {/each}
      </div>
    </section>

    <!-- Безопасность -->
    <h2 class="section-title">Безопасность</h2>
    <section class="card row">
      <div class="row-texts">
        <h3>Биометрия входа</h3>
        <p>Отпечаток / Face ID вместо PIN</p>
      </div>
      <Toggle checked={biometricOn} onchange={toggleBiometric} ariaLabel="Биометрия входа" />
    </section>
    <section class="card row">
      <div class="row-texts">
        <h3>PIN-код входа</h3>
        <p>{pinEnabled() ? 'Включён' : 'Отключён'}</p>
      </div>
      <button type="button" class="pill-btn" onclick={openPinDialog}>
        {pinEnabled() ? 'Изменить' : 'Включить'}
      </button>
    </section>

    <!-- Обновление -->
    <h2 class="section-title">Обновление</h2>
    <section class="card row">
      <div class="row-texts">
        <h3>Версия приложения</h3>
        <p>{appVersion || '…'}</p>
      </div>
      <button
        type="button"
        class="pill-btn"
        onclick={doCheckUpdate}
        disabled={updateState.checking || updateState.downloading}
      >
        {updateState.checking ? 'Проверка…' : 'Проверить обновления'}
      </button>
    </section>

    <!-- Данные -->
    <h2 class="section-title">Данные</h2>
    <section class="card">
      <ListItem
        title="Экспорт данных"
        subtitle={dataBusy ? 'Обработка…' : 'Бэкап JSON — поделиться или скачать'}
        icon="copy"
        onclick={dataBusy ? undefined : doExport}
      />
      <ListItem
        title="Импорт данных"
        subtitle="Восстановить из бэкапа (v2 или старое приложение)"
        icon="refresh"
        onclick={dataBusy ? undefined : () => (importConfirmOpen = true)}
      />
    </section>
    <!-- невидимый механизм выбора файла — кнопка и диалог кастомные -->
    <input
      bind:this={fileInput}
      type="file"
      accept="application/json,.json"
      class="file-input-hidden"
      onchange={(e) => void onBackupFilePicked(e)}
    />
  </div>
</div>

{#if pinDialogOpen}
  <div class="pin-backdrop" role="presentation">
    <div class="pin-sheet" role="dialog" aria-modal="true">
      <h3>
        {pinMode === 'setup'
          ? pinStep === 'enter'
            ? 'Новый PIN-код'
            : 'Повторите код'
          : 'Отключение PIN'}
      </h3>
      <TextField
        label={pinMode === 'setup' ? (pinStep === 'enter' ? 'Придумайте код (4–8 цифр)' : 'Повторите код') : 'Текущий код'}
        type="password"
        inputmode="numeric"
        placeholder="••••"
        value={pinValue}
        oninput={onPinInput}
        error={pinError}
        autofocus
      />
      <div class="pin-actions">
        <button type="button" class="pill-btn" onclick={() => (pinDialogOpen = false)}>Отмена</button>
        <button type="button" class="pill-btn accent" onclick={() => void submitPin()}>
          {pinMode === 'setup' ? (pinStep === 'enter' ? 'Далее' : 'Сохранить') : 'Отключить'}
        </button>
      </div>
    </div>
  </div>
{/if}

<Dialog
  open={importConfirmOpen}
  title="Импорт бэкапа"
  message="Текущие данные будут полностью заменены данными из файла. Отменить это действие будет нельзя."
  confirmLabel="Заменить"
  danger
  onconfirm={confirmImport}
  oncancel={() => (importConfirmOpen = false)}
/>

<style>
  .account-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .avatar {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: var(--accent-soft);
    color: var(--accent);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .account-texts {
    flex: 1;
    min-width: 0;
  }
  .account-texts h3 {
    font-size: 15px;
    font-weight: 600;
    color: var(--text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .sync-line {
    font-size: 13px;
    color: var(--text-muted);
  }
  .queue-badge {
    background: var(--warn);
    color: var(--bg);
    font-size: 12px;
    font-weight: 700;
    border-radius: 999px;
    padding: 2px 9px;
    font-variant-numeric: tabular-nums;
  }
  .account-actions {
    display: flex;
    gap: 10px;
    margin-top: 14px;
  }
  .pill-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 13px;
    font-weight: 600;
    border-radius: 12px;
    padding: 10px 14px;
    min-height: 42px;
    cursor: pointer;
  }
  .pill-btn:disabled {
    opacity: 0.5;
  }
  .pill-btn.danger {
    color: var(--accent);
  }
  .pill-btn.accent {
    background: var(--accent);
    border-color: var(--accent);
  }
  .card.row {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .row-texts {
    flex: 1;
  }
  .row-texts h3 {
    font-size: 15px;
    font-weight: 600;
    color: var(--text);
  }
  .row-texts p {
    font-size: 13px;
    color: var(--text-muted);
  }
  .card-label {
    font-size: 13px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-muted);
    margin-bottom: 10px;
  }
  .card-sub {
    font-size: 12px;
    color: var(--text-2);
    margin: -6px 0 10px;
  }
  .ring-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .ring-opt {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border: none;
    background: transparent;
    color: var(--text);
    font-size: 14px;
    padding: 11px 12px;
    border-radius: 10px;
    cursor: pointer;
    min-height: 44px;
  }
  .ring-opt:hover {
    background: var(--surface-2);
  }
  .ring-opt.selected {
    color: var(--accent);
    font-weight: 600;
    background: var(--accent-soft);
  }
  .eq {
    display: inline-flex;
    align-items: flex-end;
    gap: 2px;
    height: 14px;
  }
  .eq i {
    width: 3px;
    background: var(--accent);
    border-radius: 2px;
    animation: eq 0.9s ease-in-out infinite;
  }
  .eq i:nth-child(1) { height: 60%; }
  .eq i:nth-child(2) { height: 100%; animation-delay: 0.15s; }
  .eq i:nth-child(3) { height: 40%; animation-delay: 0.3s; }
  @keyframes eq {
    0%, 100% { transform: scaleY(0.5); }
    50% { transform: scaleY(1); }
  }
  /* кастомный sheet поверх Dialog для PIN */
  .pin-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    z-index: 180;
    display: flex;
    align-items: flex-end;
    justify-content: center;
  }
  .pin-sheet {
    width: 100%;
    max-width: 480px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-bottom: none;
    border-radius: 24px 24px 0 0;
    padding: 20px 20px calc(24px + env(safe-area-inset-bottom, 0px));
    display: flex;
    flex-direction: column;
    gap: 14px;
    animation: rise 280ms var(--ease);
  }
  .pin-sheet h3 {
    font-size: 17px;
    font-weight: 600;
    color: var(--text);
  }
  .pin-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
  .file-input-hidden {
    position: fixed;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }
  @keyframes rise {
    from { transform: translateY(100%); }
    to { transform: translateY(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    .pin-sheet, .eq i { animation: none; }
  }
</style>
