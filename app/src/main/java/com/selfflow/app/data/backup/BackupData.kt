package com.selfflow.app.data.backup

import com.selfflow.app.domain.model.Note
import com.selfflow.app.domain.model.Routine
import com.selfflow.app.domain.model.Task

internal data class BackupData(
    val routines: List<Routine>,
    val tasks: List<Task>,
    val notes: List<Note>
)
