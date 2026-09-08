package eu.darken.apl.main.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A batch operation that was sent to the server but whose result the app has not applied yet.
 * The row survives process death so the operation can be replayed under the same id, which the
 * server answers from its result store instead of charging the allowance again.
 */
@Entity(
    tableName = "pending_operations",
)
data class PendingOperationEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    /** JSON array of the local ids the submitted items belong to, in submission order. */
    @ColumnInfo(name = "owner_ids") val ownerIds: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "result_json") val resultJson: String? = null,
)

@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity)

    /** Reusing an id must keep the row it belongs to, including its result and its age. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(operation: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations")
    suspend fun getAll(): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE kind = :kind ORDER BY created_at ASC")
    suspend fun getByKind(kind: String): List<PendingOperationEntity>

    @Query("UPDATE pending_operations SET result_json = :resultJson WHERE operation_id = :operationId")
    suspend fun setResult(operationId: String, resultJson: String)

    @Query("DELETE FROM pending_operations WHERE operation_id = :operationId")
    suspend fun delete(operationId: String)

    @Query("DELETE FROM pending_operations WHERE created_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
