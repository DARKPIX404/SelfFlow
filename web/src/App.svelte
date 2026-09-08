<script lang="ts">
  import { session } from '$lib/auth/session.svelte'
  import { route } from '$lib/nav.svelte'
  import { syncAll } from '$lib/sync/sync'
  import { getSetting, pinEnabled, pinLock, initTheme } from '$lib/settings.svelte'
  import { haptic } from '$lib/ui/haptics'
  import BottomTabBar from '$lib/ui/BottomTabBar.svelte'
  import Toast from '$lib/ui/Toast.svelte'
  import LoginScreen from './screens/auth/LoginScreen.svelte'
  import RegisterScreen from './screens/auth/RegisterScreen.svelte'
  import TodayScreen from './screens/today/TodayScreen.svelte'
  import RoutinesScreen from './screens/routines/RoutinesScreen.svelte'
  import TasksScreen from './screens/tasks/TasksScreen.svelte'
  import NotesScreen from './screens/notes/NotesScreen.svelte'
  import NoteEditorScreen from './screens/notes/NoteEditorScreen.svelte'
  import MoreScreen from './screens/more/MoreScreen.svelte'
  import StatsScreen from './screens/more/StatsScreen.svelte'
  import AlarmScreen from './screens/more/AlarmScreen.svelte'
  import SettingsScreen from './screens/more/SettingsScreen.svelte'
  import HabitsScreen from './screens/more/HabitsScreen.svelte'
  import GoalsScreen from './screens/more/GoalsScreen.svelte'
  import FocusScreen from './screens/more/FocusScreen.svelte'
  import OnboardingScreen from './screens/onboarding/OnboardingScreen.svelte'
  import LockScreen from './screens/onboarding/LockScreen.svelte'

  initTheme()

  let isRegister = $state(window.location.hash === '#/register')
  window.addEventListener('hashchange', () => {
    isRegister = window.location.hash === '#/register'
  })

  let onboardingDone = $state(getSetting('onboarding_completed') === '1')
  window.addEventListener('selfflow:mutated', () => {
    onboardingDone = getSetting('onboarding_completed') === '1'
  })

  // PIN-блокировка при старте приложения
  pinLock.locked = pinEnabled()

  $effect(() => {
    if (session.user) void syncAll()
  })

  function onFirstInteraction() {
    // однократная тактильная реакция, чтобы «разбудить» веб-стек после жеста
    haptic('light')
    window.removeEventListener('pointerdown', onFirstInteraction)
  }
  window.addEventListener('pointerdown', onFirstInteraction)
</script>

{#if pinLock.locked}
  <LockScreen />
{:else if !session.user}
  {#if isRegister}
    <RegisterScreen />
  {:else}
    <LoginScreen />
  {/if}
{:else}
  <div class="shell">
    {#if route.tab === 'today'}
      <TodayScreen />
    {:else if route.tab === 'routines'}
      <RoutinesScreen />
    {:else if route.tab === 'tasks'}
      <TasksScreen />
    {:else if route.tab === 'notes'}
      {#if route.stack[0] === 'notes' && route.stack[1]}
        <NoteEditorScreen noteId={route.stack[1]} />
      {:else}
        <NotesScreen />
      {/if}
    {:else if route.tab === 'more'}
      {#if route.stack[0] === 'stats'}
        <StatsScreen />
      {:else if route.stack[0] === 'alarm'}
        <AlarmScreen />
      {:else if route.stack[0] === 'settings'}
        <SettingsScreen />
      {:else if route.stack[0] === 'habits'}
        <HabitsScreen />
      {:else if route.stack[0] === 'goals'}
        <GoalsScreen />
      {:else if route.stack[0] === 'focus'}
        <FocusScreen />
      {:else}
        <MoreScreen />
      {/if}
    {/if}
  </div>
  <BottomTabBar />
  {#if !onboardingDone}
    <OnboardingScreen />
  {/if}
{/if}
<Toast />

<style>
  .shell {
    animation: screenIn var(--dur-std) var(--ease);
  }
  @keyframes screenIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @media (prefers-reduced-motion: reduce) {
    .shell { animation: none; }
  }
</style>
