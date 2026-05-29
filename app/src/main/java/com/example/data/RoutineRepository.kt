package com.example.data

import kotlinx.coroutines.flow.Flow

class RoutineRepository(private val routineDao: RoutineDao) {
    val allRoutines: Flow<List<RoutineEntity>> = routineDao.getAllRoutines()
    val activeRoutine: Flow<RoutineEntity?> = routineDao.getActiveRoutineFlow()

    suspend fun getAllRoutinesList(): List<RoutineEntity> = routineDao.getAllRoutinesList()

    suspend fun getRoutineById(id: Int): RoutineEntity? {
        return routineDao.getRoutineById(id)
    }

    suspend fun getActiveRoutine(): RoutineEntity? {
        return routineDao.getActiveRoutine()
    }

    suspend fun insert(routine: RoutineEntity): Long {
        return routineDao.insertRoutine(routine)
    }

    suspend fun update(routine: RoutineEntity) {
        routineDao.updateRoutine(routine)
    }

    suspend fun delete(routine: RoutineEntity) {
        routineDao.deleteRoutine(routine)
    }

    suspend fun deactivateAll() {
        routineDao.deactivateAllRoutines()
    }

    suspend fun activateRoutine(id: Int) {
        routineDao.activateRoutine(id)
    }
}
