package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.RoutineEntity
import com.example.data.RoutineRepository
import com.example.data.Task
import com.example.service.RoutineNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class Screen {
    object Home : Screen()
    data class AddEdit(val routineId: Int? = null) : Screen()
    data class Detail(val routineId: Int) : Screen()
    object Settings : Screen()
}

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RoutineRepository

    val allRoutines: StateFlow<List<RoutineEntity>>
    val activeRoutine: StateFlow<RoutineEntity?>

    // Screen State
    var currentScreen = mutableStateOf<Screen>(Screen.Home)
        private set

    // Editor States
    var editRoutineName = mutableStateOf("")
    var editPlayRingtone = mutableStateOf(false)
    val editTasks = mutableStateListOf<Task>()
    var editErrorText = mutableStateOf<String?>(null)

    // Global Settings
    var notificationsEnabled = mutableStateOf(true)
    var soundEnabled = mutableStateOf(true)

    init {
        val routineDao = AppDatabase.getDatabase(application).routineDao()
        repository = RoutineRepository(routineDao)

        // Observe database
        allRoutines = repository.allRoutines
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        activeRoutine = repository.activeRoutine
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        // Load settings preference
        loadSettings()
    }

    fun navigateTo(screen: Screen) {
        currentScreen.value = screen
        if (screen is Screen.AddEdit) {
            val rid = screen.routineId
            editErrorText.value = null
            if (rid != null) {
                // Initialize editor with existing
                viewModelScope.launch {
                    val entity = repository.getRoutineById(rid)
                    if (entity != null) {
                        editRoutineName.value = entity.name
                        editPlayRingtone.value = entity.playRingtone
                        editTasks.clear()
                        editTasks.addAll(entity.getTasks())
                    }
                }
            } else {
                // New routine
                editRoutineName.value = ""
                editPlayRingtone.value = false
                editTasks.clear()
                // Add one blank task by default
                addNewBlankTask()
            }
        }
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            val wasActive = routine.isActive
            repository.delete(routine)
            if (wasActive) {
                Log.d("RoutineViewModel", "Active routine deleted, stopping service")
                RoutineNotificationService.stop(getApplication())
            }
        }
    }

    fun toggleRoutineActive(routineId: Int, currentIsActive: Boolean) {
        viewModelScope.launch {
            if (currentIsActive) {
                // Turn off
                repository.deactivateAll()
                // Stop Service
                RoutineNotificationService.stop(getApplication())
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "All routines deactivated", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Turn on (and deactivate others automatically)
                repository.activateRoutine(routineId)
                // Start/Restart Service
                RoutineNotificationService.start(getApplication())
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Routine activated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun addNewBlankTask() {
        val nextId = (editTasks.maxOfOrNull { it.id } ?: 0) + 1
        editTasks.add(
            0,
            Task(
                id = nextId,
                time = "08:00",
                days = listOf(1, 2, 3, 4, 5), // Weekdays Mon-Fri default
                title = "New Reminder",
                message = "Task reminder message"
            )
        )
    }

    fun removeTaskAt(index: Int) {
        if (index in editTasks.indices) {
            editTasks.removeAt(index)
        }
    }

    fun parseJsonInput(jsonText: String): Boolean {
        try {
            val arr = JSONArray(jsonText)
            val parsedList = mutableListOf<Task>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                parsedList.add(Task.fromJson(obj))
            }
            if (parsedList.isNotEmpty()) {
                editTasks.clear()
                editTasks.addAll(parsedList)
                editErrorText.value = null
                return true
            } else {
                editErrorText.value = "JSON array is empty"
            }
        } catch (e: Exception) {
            editErrorText.value = "Invalid JSON structure. Error: ${e.localizedMessage}"
            Log.e("RoutineViewModel", "Json parse failed", e)
        }
        return false
    }

    fun saveRoutine(routineIdToUpdate: Int?) {
        val name = editRoutineName.value.trim()
        if (name.isEmpty()) {
            editErrorText.value = "Routine name cannot be empty"
            return
        }
        if (editTasks.isEmpty()) {
            editErrorText.value = "A routine must contain at least one task"
            return
        }

        editErrorText.value = null
        val serializedTasks = RoutineEntity.serializeTasks(editTasks)

        viewModelScope.launch {
            if (routineIdToUpdate != null) {
                // Edit existing
                val existing = repository.getRoutineById(routineIdToUpdate)
                if (existing != null) {
                    val updated = existing.copy(name = name, tasksJson = serializedTasks, playRingtone = editPlayRingtone.value)
                    repository.update(updated)
                    // If this was active, restart service to reload task structure!
                    if (updated.isActive) {
                        RoutineNotificationService.start(getApplication())
                    }
                }
            } else {
                // Create new
                val newEntity = RoutineEntity(name = name, tasksJson = serializedTasks, isActive = false, playRingtone = editPlayRingtone.value)
                repository.insert(newEntity)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Routine saved successfully", Toast.LENGTH_SHORT).show()
                navigateTo(Screen.Home)
            }
        }
    }

    // Toggle day selection for a task in editor
    fun toggleDayInTask(taskIndex: Int, dayIndex: Int) {
        if (taskIndex in editTasks.indices) {
            val task = editTasks[taskIndex]
            val updatedDays = if (task.days.contains(dayIndex)) {
                task.days.filter { it != dayIndex }
            } else {
                (task.days + dayIndex).sorted()
            }
            editTasks[taskIndex] = task.copy(days = updatedDays)
        }
    }

    // Update time of task in editor
    fun updateTaskTime(taskIndex: Int, newTime: String) {
        if (taskIndex in editTasks.indices) {
            val task = editTasks[taskIndex]
            editTasks[taskIndex] = task.copy(time = newTime)
        }
    }

    // Update title of task in editor
    fun updateTaskTitle(taskIndex: Int, newTitle: String) {
        if (taskIndex in editTasks.indices) {
            val task = editTasks[taskIndex]
            editTasks[taskIndex] = task.copy(title = newTitle)
        }
    }

    // Update message of task in editor
    fun updateTaskMessage(taskIndex: Int, newMessage: String) {
        if (taskIndex in editTasks.indices) {
            val task = editTasks[taskIndex]
            editTasks[taskIndex] = task.copy(message = newMessage)
        }
    }

    // Settings
    private fun loadSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        notificationsEnabled.value = prefs.getBoolean("notifications_enabled", true)
        soundEnabled.value = prefs.getBoolean("sound_enabled", true)
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        
        // Notify background service by starting it again (if active)
        viewModelScope.launch {
            if (repository.getActiveRoutine() != null) {
                RoutineNotificationService.start(getApplication())
            }
        }
    }

    fun updateSoundEnabled(enabled: Boolean) {
        soundEnabled.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }
}
