package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloned_repos")
data class ClonedRepo(
    @PrimaryKey val id: Long,
    val owner: String,
    val name: String,
    val fullName: String,
    val description: String?,
    val defaultBranch: String,
    val selectedBranch: String,
    val isPrivate: Boolean,
    val clonedAt: Long,
    val lastSyncedAt: Long
)

@Entity(tableName = "cached_files")
data class CachedFile(
    @PrimaryKey val id: String, // format: "repoId/path"
    val repoId: Long,
    val path: String,
    val name: String,
    val type: String, // "file" or "dir"
    val sha: String,
    val size: Long,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long = 0L
)

@Entity(tableName = "local_drafts")
data class LocalDraft(
    @PrimaryKey val id: String, // format: "repoId/path"
    val repoId: Long,
    val path: String,
    val localContent: String,
    val isPendingPush: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val commitMessage: String? = null
)
