package com.myagent.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.myagent.app.data.db.entity.WorldBookEntryEntity
import kotlinx.coroutines.flow.Flow

/** 世界书条目的数据访问接口。 */
@Dao
interface WorldBookDao {

    @Query("SELECT * FROM worldbook_entry WHERE conversation_id = :conversationId ORDER BY priority DESC, id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<WorldBookEntryEntity>>

    @Query(
        "SELECT * FROM worldbook_entry WHERE conversation_id = :conversationId " +
            "AND enabled = 1 ORDER BY priority DESC, id ASC"
    )
    suspend fun getEnabledByConversation(conversationId: Long): List<WorldBookEntryEntity>

    @Insert
    suspend fun insert(entity: WorldBookEntryEntity): Long

    @Update
    suspend fun update(entity: WorldBookEntryEntity)

    @Delete
    suspend fun delete(entity: WorldBookEntryEntity)
}
