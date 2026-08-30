# SelfFlow — гайд по дизайну, UX и UI-библиотекам

> Собрано для того, чтобы приложение перестало быть "просто Material3" и стало плавным, отзывчивым и приятным в использовании.

---

## 1. Почему сейчас интерфейс выглядит "неплавно"

В текущей версии SelfFlow используются базовые компоненты Material3 "из коробки":

- `Scaffold`, `Card`, `TopAppBar`, `OutlinedButton`, `Switch` — без кастомизации.
- Переходы между экранами — стандартные, без анимаций.
- Списки — статичные, элементы появляются резко.
- Пустые состояния — текст и иконка, без иллюстраций/анимаций.
- Нет фидбека при взаимодействии (вибро, звук, анимация нажатия).

Это не ошибка, но это "скелет" интерфейса. Дальше нужно добавить **motion**, **микровзаимодействия**, **состояния загрузки** и **единую визуальную систему**.

---

## 2. Базовые принципы Material Design 3

> Источники: [Material Design 3](https://m3.material.io/), [10 Key Takeaways from Google's Material Design Guidelines](https://uxdesign.cc/10-key-takeaways-from-googles-material-design-guidelines-3b0867f0465a)

### 2.1. Иерархия и читаемость

- **Цвет**: используйте роли Material3 (`primary`, `onPrimary`, `surfaceVariant`, `outline`). Не хардкодьте цвета.
- **Типографика**: `display`, `headline`, `title`, `body`, `label` — каждая роль имеет свою задачу.
- **Отступы**: базовая сетка 4 dp/8 dp. Карточки — 16 dp, внутренние отступы — 12–16 dp.
- **Теневая/подъёмная иерархия**: важные элементы ближе к пользователю (`Card`, `FAB`), второстепенные — плоские.

### 2.2. Touch targets

- Минимальный размер интерактивной области — **48 × 48 dp**.
- Расстояние между кнопками — не менее 8 dp.

### 2.3. Обратная связь

- Любое действие пользователя должно давать отклик: ripple, вибро, снекбар, анимация.
- Пустые состояния должны объяснять, что делать дальше.

---

## 3. Motion и анимации

> Источник: [Material Motion Guidelines](https://m3.material.io/styles/motion/overview)

### 3.1. Переходы между экранами

Вместо резкой смены экрана используйте анимированную навигацию:

```kotlin
// androidx.navigation:navigation-compose + анимации
composable(
    route = Screen.Tasks.route,
    enterTransition = { slideInHorizontally { it } + fadeIn() },
    exitTransition = { slideOutHorizontally { -it } + fadeOut() },
    popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
    popExitTransition = { slideOutHorizontally { it } + fadeOut() }
) { TasksScreen() }
```

### 3.2. Анимация появления элементов списка

```kotlin
@Composable
fun AnimatedListItem(index: Int, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(animationSpec = tween(300)) { it / 2 }
    ) {
        content()
    }
}
```

### 3.3. Микро-анимации

- `animateContentSize()` — плавное изменение высоты карточек.
- `AnimatedContent` — переключение между состояниями (например, пустой список ↔ список задач).
- `Crossfade` — смена вкладок/фильтров.

---

## 4. Рекомендуемые UI/UX библиотеки

### 4.1. Изображения и иллюстрации

**Coil** — загрузка изображений из сети/диска с плейсхолдерами.

```kotlin
implementation("io.coil-kt:coil-compose:2.6.0")
```

**Lottie Compose** — векторные анимации для пустых состояний, успеха, ошибок.

```kotlin
implementation("com.airbnb.android:lottie-compose:6.4.1")
```

Пример:

```kotlin
@Composable
fun EmptyStateAnimation() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.empty_state))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = Modifier.size(180.dp)
    )
}
```

### 4.2. Placeholder / shimmer при загрузке

**Accompanist Placeholder** (уже есть в проекте через Accompanist):

```kotlin
implementation("com.google.accompanist:accompanist-placeholder-material3:0.34.0")
```

Использование:

```kotlin
Text(
    text = title,
    modifier = Modifier.placeholder(
        visible = isLoading,
        color = MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(4.dp)
    )
)
```

### 4.3. Навигация с анимацией

```kotlin
implementation("androidx.navigation:navigation-compose:2.7.7")
```

Используйте `enterTransition`/`exitTransition` на уровне `NavHost`.

### 4.4. Haptic feedback

В Compose 1.6+:

```kotlin
val haptic = LocalHapticFeedback.current

IconButton(
    onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onDelete()
    }
) { /* ... */ }
```

Или через `View`:

```kotlin
val view = LocalView.current
view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
```

### 4.5. Pull-to-refresh

Уже используется `com.google.accompanist:accompanist-swiperefresh:0.34.0`. Можно улучшить:

- Кастомный индикатор с цветом `primary`.
- Haptic feedback при достижении порога обновления.

---

## 5. Конкретные улучшения для SelfFlow

### 5.1. Экраны списков (Tasks, Notes, Routine)

- [ ] Добавить **анимацию появления** элементов при первой загрузке.
- [ ] Добавить **shimmer placeholder** во время загрузки.
- [ ] Swipe-to-delete: показывать иконку корзины с цветом `errorContainer`/`onErrorContainer`.
- [ ] Пустое состояние: Lottie-анимация + кнопка "Добавить первую задачу".
- [ ] Haptic feedback при свайпе, удалении, добавлении.

### 5.2. Навигация

- [ ] Анимирированные переходы между экранами (`slide + fade`).
- [ ] Подсветка активного пункта нижней панели с анимацией масштаба/цвета.
- [ ] При нажатии FAB — появление bottom sheet с анимацией `slideInVertically`.

### 5.3. Диалоги и bottom sheets

- [ ] Диалоги добавления задач/заметок/распорядка — через `ModalBottomSheet` вместо `AlertDialog`.
- [ ] Анимация раскрытия bottom sheet.
- [ ] Подтверждение удаления — `Snackbar` с кнопкой "Отмена".

### 5.4. Экран блокировки (LockScreen)

- [ ] Анимация "тряски" PIN-точек при неверном коде.
- [ ] Haptic feedback при нажатии каждой цифры.
- [ ] Плавное исчезновение LockScreen при успешном вводе.

### 5.5. Статистика

- [ ] Анимация отрисовки графиков (постепенное появление секторов/столбцов).
- [ ] Цвета секторов — из Material3 color scheme.
- [ ] Показ значения при тапе на сектор.

### 5.6. Общие улучшения

- [ ] Единый `Shape` для карточек (16 dp), кнопок (12 dp), полей ввода (8 dp).
- [ ] Цветовая схема: использовать `seed` color ближе к теме саморазвития (спокойный синий/зелёный).
- [ ] Тёмная тема: проверить контрастность в `surfaceVariant`.
- [ ] Accessibility: протестировать TalkBack, увеличенный шрифт, высокую контрастность.

---

## 6. Accessibility (доступность)

> Источник: [Accessibility | Material Design](https://m2.material.io/design/usability/accessibility.html)

- **Content descriptions** для всех иконок и изображений.
- **Touch targets** минимум 48 dp.
- **Контрастность** текста к фону не менее 4.5:1.
- Поддержка **TalkBack**: логичный порядок чтения, grouping элементов.
- Тестирование с включённым **Remove animations** в настройках разработчика.

---

## 7. Рекомендуемый порядок внедрения

1. **Motion**: анимации переходов + появления списков. Максимальный эффект при минимальных изменениях.
2. **Empty states + placeholders**: Lottie + shimmer. Убирает ощущение "пустого" приложения.
3. **Haptic feedback**: вибро на ключевых действиях. Делается быстро.
4. **Bottom sheets + диалоги**: улучшает UX на маленьких экранах.
5. **Цвет/типографика**: финальная полировка визуальной системы.
6. **Accessibility**: обязательный финальный аудит перед релизом.

---

## 8. Полезные ссылки

- [Material Design 3](https://m3.material.io/)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Jetpack Compose Animation](https://developer.android.com/develop/ui/compose/animation)
- [Accompanist](https://github.com/google/accompanist)
- [Lottie for Compose](https://github.com/airbnb/lottie/blob/master/android-compose.md)
- [Coil Compose](https://coil-kt.github.io/coil/compose/)
- [Android Accessibility Developer Checklist](https://developer.android.com/guide/topics/ui/accessibility/checklist)
- [Popular Animation libraries for Jetpack Compose](https://www.jetpackcompose.app/Animation-libraries-in-Jetpack-Compose)
