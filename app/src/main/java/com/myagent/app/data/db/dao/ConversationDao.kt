package com.myagent.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.myagent.app.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/** 会话的数据访问接口。 */
@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversation ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversation WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Delete
    suspend fun delete(entity: ConversationEntity)

    @Query("DELETE FROM conversation WHERE id = :id")
    suspend fun deleteById(id: Long)
}
