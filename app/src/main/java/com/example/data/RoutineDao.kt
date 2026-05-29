package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY id DESC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY id DESC")
    suspend fun getAllRoutinesList(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getRoutineById(id: Int): RoutineEntity?

    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRoutine(): RoutineEntity?

    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    fun getActiveRoutineFlow(): Flow<RoutineEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("UPDATE routines SET isActive = 0")
    suspend fun deactivateAllRoutines()

    @Transaction
    suspend fun activateRoutine(routineId: Int) {
        deactivateAllRoutines()
        val routine = getRoutineById(routineId)
        if (routine != null) {
            updateRoutine(routine.copy(isActive = true))
        }
    }
}
