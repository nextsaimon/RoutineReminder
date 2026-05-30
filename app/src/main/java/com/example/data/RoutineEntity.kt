package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

data class Task(
    val id: Int,
    val time: String,      // "06:00"
    val days: List<Int>,   // 0 = Sunday, 1 = Monday ... 6 = Saturday
    val title: String,
    val message: String,
    val playRingtone: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("time", time)
        val daysArray = JSONArray()
        days.forEach { daysArray.put(it) }
        obj.put("days", daysArray)
        obj.put("title", title)
        obj.put("message", message)
        obj.put("playRingtone", playRingtone)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): Task {
            val id = obj.optInt("id", 0)
            val time = obj.optString("time", "00:00")
            val daysArray = obj.optJSONArray("days")
            val days = mutableListOf<Int>()
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    days.add(daysArray.getInt(i))
                }
            }
            val title = obj.optString("title", "")
            val message = obj.optString("message", "")
            val playRingtone = obj.optBoolean("playRingtone", false)
            return Task(id, time, days, title, message, playRingtone)
        }
    }
}

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isActive: Boolean = false,
    val tasksJson: String = "[]", // JSON representation of List<Task>
    val playRingtone: Boolean = false,
    val ringtoneDuration: Int = 20
) {
    fun getTasks(): List<Task> {
        val list = mutableListOf<Task>()
        try {
            val arr = JSONArray(tasksJson)
            for (i in 0 until arr.length()) {
                list.add(Task.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    companion object {
        fun serializeTasks(tasks: List<Task>): String {
            val arr = JSONArray()
            tasks.sortedBy { it.id }.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
