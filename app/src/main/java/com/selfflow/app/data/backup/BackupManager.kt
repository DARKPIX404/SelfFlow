package com.selfflow.app.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.selfflow.app.data.repository.NoteRepository
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.TaskRepository
import com.selfflow.app.domain.model.Note
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Менеджер резервного копирования.
 *
 * Экспортирует все рутины, задачи и заметки в JSON-файл через Storage Access Framework.
 * Импорт заменяет текущие данные содержимым backup-файла (безопасный простой подход).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository
) {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .setPrettyPrinting()
        .create()

    /**
     * Экспортирует все данные по указанному URI.
     *
     * @return количество экспортированных записей в случае успеха.
     */
    suspend fun exportToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val routines = routineRepository.getAll().first()
            val tasks = taskRepository.getAll().first()
            val notes = noteRepository.getAll().first()

            val backup = BackupData(routines, tasks, notes)

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    gson.toJson(backup, writer)
                }
            } ?: error("Не удалось открыть файл для записи")

            routines.size + tasks.size + notes.size
        }
    }

    /**
     * Импортирует данные из backup-файла по указанному URI.
     *
     * Перед импортом все текущие данные удаляются, затем вставляются записи из файла.
     * Идентификаторы сбрасываются, чтобы избежать конфликтов автоинкремента.
     *
     * @return количество импортированных записей в случае успеха.
     */
    suspend fun importFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = context.contentResolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    gson.fromJson(reader, BackupData::class.java)
                }
            } ?: error("Не удалось открыть файл для чтения")

            // Удаляем текущие данные (replace-стратегия).
            routineRepository.getAll().first().forEach { routineRepository.delete(it) }
            taskRepository.getAll().first().forEach { taskRepository.delete(it) }
            noteRepository.getAll().first().forEach { noteRepository.delete(it) }

            // Вставляем записи из backup со сброшенным id.
            backup.routines.forEach { routineRepository.insert(it.copy(id = 0L)) }
            backup.tasks.forEach { taskRepository.insert(it.copy(id = 0L)) }
            backup.notes.forEach { noteRepository.insert(it.copy(id = 0L)) }

            backup.routines.size + backup.tasks.size + backup.notes.size
        }
    }

    private class InstantTypeAdapter : TypeAdapter<Instant>() {
        override fun write(out: JsonWriter, value: Instant?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.toEpochMilli())
            }
        }

        override fun read(`in`: JsonReader): Instant? {
            return if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
                `in`.nextNull()
                null
            } else {
                Instant.ofEpochMilli(`in`.nextLong())
            }
        }
    }
}
