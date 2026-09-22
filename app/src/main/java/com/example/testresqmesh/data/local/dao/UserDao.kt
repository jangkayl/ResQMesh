package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getUserByDeviceId(deviceId: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt ASC LIMIT 1")
    suspend fun getLocalUser(): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt ASC LIMIT 1")
    fun observeLocalUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("UPDATE users SET displayName = :displayName WHERE userId = :userId")
    suspend fun updateDisplayName(userId: String, displayName: String): Int
}
