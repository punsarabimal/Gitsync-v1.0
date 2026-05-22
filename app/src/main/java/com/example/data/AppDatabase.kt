package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM cloned_repos ORDER BY clonedAt DESC")
    fun getAllClonedReposFlow(): Flow<List<ClonedRepo>>

    @Query("SELECT * FROM cloned_repos ORDER BY clonedAt DESC")
    suspend fun getAllClonedRepos(): List<ClonedRepo>

    @Query("SELECT * FROM cloned_repos WHERE id = :id LIMIT 1")
    suspend fun getRepoById(id: Long): ClonedRepo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepo(repo: ClonedRepo)

    @Delete
    suspend fun deleteRepo(repo: ClonedRepo)

    @Query("DELETE FROM cloned_repos WHERE id = :id")
    suspend fun deleteRepoById(id: Long)
}

@Dao
interface CachedFileDao {
    @Query("SELECT * FROM cached_files WHERE repoId = :repoId ORDER BY type DESC, name ASC")
    fun getFilesForRepoFlow(repoId: Long): Flow<List<CachedFile>>

    @Query("SELECT * FROM cached_files WHERE repoId = :repoId AND path = :path LIMIT 1")
    suspend fun getByPath(repoId: Long, path: String): CachedFile?

    @Query("SELECT * FROM cached_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<CachedFile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: CachedFile)

    @Query("DELETE FROM cached_files WHERE repoId = :repoId")
    suspend fun deleteFilesForRepo(repoId: Long)

    @Query("DELETE FROM cached_files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("SELECT * FROM cached_files WHERE isFavorite = 1")
    fun getFavoriteFilesFlow(): Flow<List<CachedFile>>

    @Query("SELECT * FROM cached_files WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT 20")
    fun getRecentFilesFlow(): Flow<List<CachedFile>>
}

@Dao
interface LocalDraftDao {
    @Query("SELECT * FROM local_drafts WHERE repoId = :repoId")
    fun getDraftsForRepoFlow(repoId: Long): Flow<List<LocalDraft>>

    @Query("SELECT * FROM local_drafts WHERE repoId = :repoId AND path = :path LIMIT 1")
    suspend fun getDraft(repoId: Long, path: String): LocalDraft?

    @Query("SELECT * FROM local_drafts WHERE id = :id LIMIT 1")
    suspend fun getDraftById(id: String): LocalDraft?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: LocalDraft)

    @Query("DELETE FROM local_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: String)

    @Query("DELETE FROM local_drafts WHERE repoId = :repoId AND path = :path")
    suspend fun deleteDraft(repoId: Long, path: String)

    @Query("DELETE FROM local_drafts WHERE repoId = :repoId")
    suspend fun deleteDraftsForRepo(repoId: Long)
}

@Database(entities = [ClonedRepo::class, CachedFile::class, LocalDraft::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun cachedFileDao(): CachedFileDao
    abstract fun localDraftDao(): LocalDraftDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gitsync_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
