<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import AppBar from '$lib/ui/AppBar.svelte'
  import Icon from '$lib/ui/Icon.svelte'
  import TimeWheel from '$lib/ui/TimeWheel.svelte'
  import Toggle from '$lib/ui/Toggle.svelte'
  import { getSetting, setSetting } from '$lib/settings.svelte'
  import { haptic } from '$lib/ui/haptics'
  import { showToast } from '$lib/ui/toast.svelte'
  import { rescheduleAlarms, requestRescheduleReminders } from '$lib/notifications'
  import { AlarmOverlay, type AlarmOverlayPermissions } from '$lib/native/alarmOverlay'
  import { LocalNotifications } from '@capacitor/local-notifications'
  import { Capacitor } from '@capacitor/core'

  let now = $state(new Date())
  const timer = setInterval(() => (now = new Date()), 1000)
  onDestroy(() => clearInterval(timer))

  let wakeTime = $state(getSetting('wake_time') ?? '07:00')
  let sleepTime = $state(getSetting('sleep_time') ?? '23:00')
  let enabled = $state(getSetting('alarm_enabled') === '1')

  const pad2 = (n: number) => String(n).padStart(2, '0')
  let clock = $derived(`${pad2(now.getHours())}:${pad2(now.getMinutes())}:${pad2(now.getSeconds())}`)

  const native = Capacitor.isNativePlatform()
  let perms = $state<AlarmOverlayPermissions & { reminders: boolean } | null>(null)

  async function refreshPermissions() {
    if (!native) return
    try {
      const [p, r] = await Promise.all([
        AlarmOverlay.checkPermissions(),
        LocalNotifications.checkPermissions(),
      ])
      perms = { ...p, reminders: r.display === 'granted' }
    } catch {
      perms = null
    }
  }

  onMount(() => {
    void refreshPermissions()
  })

  function saveWake(v: string | null) {
    wakeTime = v ?? wakeTime
    setSetting('wake_time', wakeTime)
    replan()
  }

  function saveSleep(v: string | null) {
    sleepTime = v ?? sleepTime
    setSetting('sleep_time', sleepTime)
    replan()
  }

  function replan() {
    void rescheduleAlarms()
    requestRescheduleReminders()
  }

  function toggleAlarm(v: boolean) {
    haptic('light')
    enabled = v
    setSetting('alarm_enabled', v ? '1' : '0')
    replan()
    showToast({ message: v ? 'Будильник запланирован' : 'Будильник выключен' })
  }

  async function checkOverlay() {
    haptic('light')
    if (!native) {
      showToast({ message: 'Оверлей доступен только в нативном приложении' })
      return
    }
    try {
      await AlarmOverlay.showOverlayNow({ title: 'SelfFlow', text: 'Так будет выглядеть будильник' })
    } catch {
      showToast({ message: 'Не удалось показать оверлей' })
    }
  }

  async function requestOverlay() {
    haptic('light')
    await AlarmOverlay.requestOverlayPermission()
    await refreshPermissions()
  }

  async function requestExact() {
    haptic('light')
    await AlarmOverlay.requestExactAlarmPermission()
    await refreshPermissions()
  }

  async function requestReminderPerms() {
    haptic('light')
    await LocalNotifications.requestPermissions()
    replan()
    await refreshPermissions()
  }
</script>

<div class="screen">
  <AppBar title="Будильник" large />
  <div class="screen-body alarm-body">
    <div class="clock-wrap">
      <span class="clock">{clock}</span>
    </div>

    <div class="plates">
      <section class="plate">
        <div class="plate-head">
          <span class="plate-icon wake"><Icon name="sun" size={22} /></span>
          <div class="plate-texts">
            <h2>Подъём</h2>
            <p>Начало дня</p>
          </div>
          <TimeWheel value={wakeTime} onchange={saveWake} allowClear={false} />
        </div>
      </section>

      <section class="plate">
        <div class="plate-head">
          <span class="plate-icon sleep"><Icon name="moon" size={22} /></span>
          <div class="plate-texts">
            <h2>Отбой</h2>
            <p>Конец дня</p>
          </div>
          <TimeWheel value={sleepTime} onchange={saveSleep} allowClear={false} />
        </div>
      </section>
    </div>

    <section class="plate row">
      <div class="plate-texts">
        <h2>Будильник</h2>
        <p>{enabled ? 'Включён' : 'Выключен'}</p>
      </div>
      <Toggle checked={enabled} onchange={toggleAlarm} ariaLabel="Будильник" />
    </section>

    <button type="button" class="check-btn" onclick={checkOverlay}>Проверить оверлей</button>

    {#if native && perms}
      <section class="perms">
        <h3 class="perms-title">Разрешения</h3>
        <div class="perm-row">
          <span class="perm-name">Точные будильники</span>
          {#if perms.exactAlarms}
            <span class="perm-ok">Выдано</span>
          {:else}
            <button type="button" class="perm-btn" onclick={requestExact}>Разрешить</button>
          {/if}
        </div>
        <div class="perm-row">
          <span class="perm-name">Уведомления</span>
          {#if perms.notifications}
            <span class="perm-ok">Выдано</span>
          {:else}
            <button type="button" class="perm-btn" onclick={requestReminderPerms}>Разрешить</button>
          {/if}
        </div>
        <div class="perm-row">
          <span class="perm-name">Поверх других приложений</span>
          {#if perms.overlay}
            <span class="perm-ok">Выдано</span>
          {:else}
            <button type="button" class="perm-btn" onclick={requestOverlay}>Разрешить</button>
          {/if}
        </div>
        <div class="perm-row">
          <span class="perm-name">Напоминания (каналы)</span>
          {#if perms.reminders}
            <span class="perm-ok">Выдано</span>
          {:else}
            <button type="button" class="perm-btn" onclick={requestReminderPerms}>Разрешить</button>
          {/if}
        </div>
      </section>
    {:else if !native}
      <p class="note">Нативные будильники и разрешения работают в Android-приложении.</p>
    {/if}
  </div>
</div>

<style>
  .alarm-body {
    gap: 16px;
  }
  .clock-wrap {
    display: flex;
    justify-content: center;
    padding: 24px 0 8px;
  }
  .clock {
    font-size: 56px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--text);
    font-variant-numeric: tabular-nums;
    line-height: 1;
  }
  .plates {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .plate {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 14px 16px;
  }
  .plate-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .plate-icon {
    width: 44px;
    height: 44px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .plate-icon.wake {
    background: var(--accent-soft);
    color: var(--warn);
  }
  .plate-icon.sleep {
    background: var(--accent-soft);
    color: var(--text-2);
  }
  .plate-texts {
    flex: 1;
    min-width: 0;
  }
  .plate-texts h2 {
    font-size: 16px;
    font-weight: 600;
    color: var(--text);
  }
  .plate-texts p {
    font-size: 13px;
    color: var(--text-muted);
  }
  .plate.row {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .check-btn {
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    font-size: 14px;
    font-weight: 600;
    border-radius: 14px;
    padding: 13px;
    min-height: 48px;
    cursor: pointer;
  }
  .perms {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .perms-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-2);
  }
  .perm-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .perm-name {
    font-size: 14px;
    color: var(--text);
  }
  .perm-ok {
    font-size: 13px;
    color: var(--ok, #6da760);
  }
  .perm-btn {
    border: 1px solid var(--border);
    background: var(--accent);
    color: var(--text);
    font-size: 13px;
    font-weight: 600;
    border-radius: 10px;
    padding: 7px 14px;
    cursor: pointer;
  }
  .note {
    font-size: 12px;
    color: var(--text-muted);
    text-align: center;
  }
</style>
