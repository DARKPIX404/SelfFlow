package com.selfflow.app.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.selfflow.app.data.local.AppDatabase
import com.selfflow.app.data.local.dao.NoteDao
import com.selfflow.app.data.local.dao.RoutineDao
import com.selfflow.app.data.local.dao.TaskDao
import com.selfflow.app.data.preferences.dataStore
import com.selfflow.app.data.repository.NoteRepository
import com.selfflow.app.data.repository.RoutineRepository
import com.selfflow.app.data.repository.RoutineTemplateRepository
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.data.repository.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalTime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "selfflow.db"
        ).build()

    @Provides
    fun provideRoutineDao(database: AppDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao = database.noteDao()

    @Provides
    @Singleton
    fun provideRoutineRepository(routineDao: RoutineDao): RoutineRepository =
        RoutineRepository(routineDao)

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao): TaskRepository =
        TaskRepository(taskDao)

    @Provides
    @Singleton
    fun provideNoteRepository(noteDao: NoteDao): NoteRepository =
        NoteRepository(noteDao)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .create()

    @Provides
    @Singleton
    fun provideRoutineTemplateRepository(
        dataStore: DataStore<Preferences>,
        gson: Gson
    ): RoutineTemplateRepository = RoutineTemplateRepository(dataStore, gson)

    private class LocalTimeAdapter : TypeAdapter<LocalTime>() {
        override fun write(out: JsonWriter, value: LocalTime?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.toString())
            }
        }

        override fun read(input: JsonReader): LocalTime? {
            return if (input.peek() == com.google.gson.stream.JsonToken.NULL) {
                input.nextNull()
                null
            } else {
                LocalTime.parse(input.nextString())
            }
        }
    }
}
