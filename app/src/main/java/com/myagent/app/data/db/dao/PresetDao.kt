package com.myagent.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myagent.app.data.db.entity.ModelPresetEntity
import kotlinx.coroutines.flow.Flow

/** 模型预设的数据访问接口。 */
@Dao
interface PresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelPresetEntity)

    @Query("SELECT * FROM model_preset ORDER BY name ASC")
    fun observeAll(): Flow<List<ModelPresetEntity>>

    @Query("SELECT * FROM model_preset WHERE id = :id")
    suspend fun getById(id: Long): ModelPresetEntity?

    @Delete
    suspend fun delete(entity: ModelPresetEntity)
}
