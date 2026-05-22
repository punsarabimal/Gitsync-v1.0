package com.example.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GitRepository(private val context: Context) {

    private val secureStorage = SecureStorage(context)
    private val database = AppDatabase.getDatabase(context)
    private val repoDao = database.repoDao()
    private val cachedFileDao = database.cachedFileDao()
    private val localDraftDao = database.localDraftDao()

    private val userPrefs = context.getSharedPreferences("gitsync_user_prefs", Context.MODE_PRIVATE)

    // Device Flow Auth State
    private var currentDeviceCodeResponse: GithubDeviceCodeResponse? = null

    // --- Authentication ---

    fun loginWithToken(token: String, onSuccess: (GithubUser) -> Unit, onError: (String) -> Unit) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val header = "token $token"
                val response = RetrofitClient.githubService.getCurrentUser(header)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    secureStorage.saveToken(token)
                    saveUserProfile(user)
                    withContext(Dispatchers.Main) {
                        onSuccess(user)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Invalid token or unauthorized: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Network or Auth Error: ${e.message}")
                }
            }
        }
    }

    private fun saveUserProfile(user: GithubUser) {
        userPrefs.edit()
            .putString("user_login", user.login)
            .putString("user_avatar", user.avatarUrl)
            .putString("user_name", user.name ?: "")
            .putString("user_email", user.email ?: "")
            .apply()
    }

    fun getSavedUserLogin(): String = userPrefs.getString("user_login", "") ?: ""
    fun getSavedUserAvatar(): String = userPrefs.getString("user_avatar", "") ?: ""
    fun getSavedUserName(): String = userPrefs.getString("user_name", "") ?: ""
    fun getSavedUserEmail(): String = userPrefs.getString("user_email", "") ?: ""

    fun hasToken(): Boolean = secureStorage.hasToken()
    fun getToken(): String = secureStorage.getToken()

    fun logout() {
        secureStorage.deleteToken()
        userPrefs.edit().clear().apply()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            // Delete all repositories & files from local DB on logout
            val repos = repoDao.getAllClonedRepos()
            for (repo in repos) {
                repoDao.deleteRepo(repo)
                cachedFileDao.deleteFilesForRepo(repo.id)
                localDraftDao.deleteDraftsForRepo(repo.id)
            }
        }
    }

    // --- Device Flow Authentication ---

    suspend fun initiateDeviceFlow(
        clientId: String
    ): Result<GithubDeviceCodeResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = GithubDeviceCodeRequest(clientId = clientId)
            val response = RetrofitClient.githubAuthService.requestDeviceCode(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                currentDeviceCodeResponse = body
                Result.success(body)
            } else {
                Result.failure(Exception("Failed to request device code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollDeviceFlowToken(
        clientId: String
    ): Result<GithubUser> = withContext(Dispatchers.IO) {
        val devCode = currentDeviceCodeResponse?.deviceCode
            ?: return@withContext Result.failure(Exception("No active device flow request"))

        return@withContext try {
            val request = GithubPollTokenRequest(clientId = clientId, deviceCode = devCode)
            val response = RetrofitClient.githubAuthService.pollAccessToken(request)
            
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                if (tokenResponse.accessToken != null) {
                    val token = tokenResponse.accessToken
                    val userHeader = "token $token"
                    val userResponse = RetrofitClient.githubService.getCurrentUser(userHeader)
                    
                    if (userResponse.isSuccessful && userResponse.body() != null) {
                        val user = userResponse.body()!!
                        secureStorage.saveToken(token)
                        saveUserProfile(user)
                        Result.success(user)
                    } else {
                        Result.failure(Exception("Successfully got token, but failed to fetch user profile."))
                    }
                } else if (tokenResponse.error != null) {
                    Result.failure(Exception(tokenResponse.errorDescription ?: tokenResponse.error))
                } else {
                    Result.failure(Exception("Unknown authentication error"))
                }
            } else {
                Result.failure(Exception("Failed polling authentication token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Repositories DB & API Flow ---

    fun getClonedReposFlow(): Flow<List<ClonedRepo>> = repoDao.getAllClonedReposFlow()

    suspend fun getRemoteRepos(): Result<List<GithubRepo>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken()
            if (token.isEmpty()) return@withContext Result.failure(Exception("Not authenticated"))
            val response = RetrofitClient.githubService.getRepositories("token $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch repos: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cloneRepository(githubRepo: GithubRepo): Result<ClonedRepo> = withContext(Dispatchers.IO) {
        try {
            val localRepo = ClonedRepo(
                id = githubRepo.id,
                owner = githubRepo.owner.login,
                name = githubRepo.name,
                fullName = githubRepo.fullName,
                description = githubRepo.description,
                defaultBranch = githubRepo.defaultBranch ?: "main",
                selectedBranch = githubRepo.defaultBranch ?: "main",
                isPrivate = githubRepo.private,
                clonedAt = System.currentTimeMillis(),
                lastSyncedAt = System.currentTimeMillis()
            )
            repoDao.insertRepo(localRepo)
            
            // Sync top-level files as the initial clone step
            val syncResult = syncFiles(localRepo.owner, localRepo.name, localRepo.id, "", localRepo.selectedBranch)
            if (syncResult.isSuccess) {
                Result.success(localRepo)
            } else {
                Result.success(localRepo) // Still return repo even if initial file sync is delayed or partial
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteClonedRepo(repoId: Long) = withContext(Dispatchers.IO) {
        repoDao.deleteRepoById(repoId)
        cachedFileDao.deleteFilesForRepo(repoId)
        localDraftDao.deleteDraftsForRepo(repoId)
    }

    // --- File Operations Flow ---

    fun getCachedFilesFlow(repoId: Long): Flow<List<CachedFile>> = cachedFileDao.getFilesForRepoFlow(repoId)
    fun getFavoriteFilesFlow(): Flow<List<CachedFile>> = cachedFileDao.getFavoriteFilesFlow()
    fun getRecentFilesFlow(): Flow<List<CachedFile>> = cachedFileDao.getRecentFilesFlow()

    suspend fun syncFiles(
        owner: String,
        repo: String,
        repoId: Long,
        directoryPath: String,
        branch: String
    ): Result<List<CachedFile>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken()
            if (token.isEmpty()) return@withContext Result.failure(Exception("Not authenticated"))
            
            val response = RetrofitClient.githubService.getRepositoryContents(
                tokenHeader = "token $token",
                owner = owner,
                repo = repo,
                path = directoryPath,
                ref = branch
            )

            if (response.isSuccessful && response.body() != null) {
                val contents = response.body()!!
                val cachedFiles = contents.map { content ->
                    val fileId = "$repoId/${content.path}"
                    
                    // Retain favorite flag if exists
                    val existing = cachedFileDao.getById(fileId)
                    val isFavorite = existing?.isFavorite ?: false
                    
                    CachedFile(
                        id = fileId,
                        repoId = repoId,
                        path = content.path,
                        name = content.name,
                        type = content.type,
                        sha = content.sha,
                        size = content.size,
                        isFavorite = isFavorite,
                        lastOpenedAt = existing?.lastOpenedAt ?: 0L
                    )
                }

                cachedFileDao.insertFiles(cachedFiles)
                Result.success(cachedFiles)
            } else {
                Result.failure(Exception("Failed to sync files: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileContent(
        owner: String,
        repo: String,
        repoId: Long,
        path: String,
        branch: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Register recent open
            cachedFileDao.getById("$repoId/$path")?.let { cached ->
                cachedFileDao.insertFile(cached.copy(lastOpenedAt = System.currentTimeMillis()))
            }

            // Check if we have a local draft that overrides GitHub
            val draft = localDraftDao.getDraftById("$repoId/$path")
            if (draft != null) {
                return@withContext Result.success(draft.localContent)
            }

            // Otherwise, pull from GitHub
            val token = getToken()
            if (token.isEmpty()) return@withContext Result.failure(Exception("Not authenticated"))
            val response = RetrofitClient.githubService.getSingleFileContent(
                tokenHeader = "token $token",
                owner = owner,
                repo = repo,
                path = path,
                ref = branch
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val rawEncodedContent = body.content ?: ""
                // Normalize spaces and decode base64
                val sanitizedEncoded = rawEncodedContent.replace("\n", "").replace("\r", "")
                val decodedBytes = Base64.decode(sanitizedEncoded, Base64.DEFAULT)
                val text = String(decodedBytes, Charsets.UTF_8)
                Result.success(text)
            } else {
                Result.failure(Exception("Failed to fetch file content: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Drafts & Editing Flow ---

    fun getLocalDraftsFlow(repoId: Long): Flow<List<LocalDraft>> = localDraftDao.getDraftsForRepoFlow(repoId)

    suspend fun saveDraft(repoId: Long, path: String, content: String) = withContext(Dispatchers.IO) {
        val draftId = "$repoId/$path"
        val draft = LocalDraft(
            id = draftId,
            repoId = repoId,
            path = path,
            localContent = content,
            isPendingPush = true,
            updatedAt = System.currentTimeMillis()
        )
        localDraftDao.insertDraft(draft)
    }

    suspend fun discardDraft(repoId: Long, path: String) = withContext(Dispatchers.IO) {
        localDraftDao.deleteDraft(repoId, path)
    }

    suspend fun toggleFavorite(repoId: Long, path: String) = withContext(Dispatchers.IO) {
        val fileId = "$repoId/$path"
        val cached = cachedFileDao.getById(fileId)
        if (cached != null) {
            cachedFileDao.insertFile(cached.copy(isFavorite = !cached.isFavorite))
        }
    }

    suspend fun createNewOfflineFile(repoId: Long, path: String, name: String, type: String) = withContext(Dispatchers.IO) {
        val fullPath = if (path.isEmpty()) name else "$path/$name"
        val fileId = "$repoId/$fullPath"
        
        val cached = CachedFile(
            id = fileId,
            repoId = repoId,
            path = fullPath,
            name = name,
            type = type,
            sha = "", // New offline file does not have SHA
            size = 0L,
            isFavorite = false,
            lastOpenedAt = 0L
        )
        cachedFileDao.insertFile(cached)
        
        if (type == "file") {
            saveDraft(repoId, fullPath, "")
        }
    }

    suspend fun renameDeleteOfflineFile(repoId: Long, path: String, action: String, newName: String? = null) = withContext(Dispatchers.IO) {
        val fileId = "$repoId/$path"
        if (action == "DELETE") {
            cachedFileDao.deleteFileById(fileId)
            localDraftDao.deleteDraft(repoId, path)
        } else if (action == "RENAME" && newName != null) {
            val cached = cachedFileDao.getById(fileId)
            if (cached != null) {
                cachedFileDao.deleteFileById(fileId)
                
                val parentPath = path.substringBeforeLast("/", "")
                val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
                
                cachedFileDao.insertFile(cached.copy(
                    id = "$repoId/$newPath",
                    path = newPath,
                    name = newName
                ))
                
                // Move draft if exists
                val draft = localDraftDao.getDraft(repoId, path)
                if (draft != null) {
                    localDraftDao.deleteDraft(repoId, path)
                    localDraftDao.insertDraft(draft.copy(
                        id = "$repoId/$newPath",
                        path = newPath
                    ))
                }
            }
        }
    }

    // --- Commit & Push Back to GitHub ---

    suspend fun commitAndPushFile(
        owner: String,
        repoName: String,
        repoId: Long,
        path: String,
        branch: String,
        commitMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getToken()
            if (token.isEmpty()) return@withContext Result.failure(Exception("Not authenticated"))

            // 1. Get current draft content
            val draft = localDraftDao.getDraft(repoId, path)
                ?: return@withContext Result.failure(Exception("No local changes to push"))

            // 2. Fetch existing file SHA if not cached
            var sha: String? = cachedFileDao.getByPath(repoId, path)?.sha
            if (sha.isNullOrEmpty()) {
                // Query server for SHA
                try {
                    val response = RetrofitClient.githubService.getSingleFileContent("token $token", owner, repoName, path, branch)
                    if (response.isSuccessful && response.body() != null) {
                        sha = response.body()!!.sha
                    }
                } catch (e: Exception) {
                    // Ignored (might be a new file)
                }
            }

            // 3. Base64 encode the draft text
            val base64Content = Base64.encodeToString(draft.localContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // 4. PUT request (Create/Update File API)
            val body = GithubFileUpdateRequest(
                message = commitMessage,
                content = base64Content,
                sha = sha,
                branch = branch
            )

            val apiResponse = RetrofitClient.githubService.createOrUpdateFile(
                tokenHeader = "token $token",
                owner = owner,
                repo = repoName,
                path = path,
                body = body
            )

            if (apiResponse.isSuccessful) {
                // Clear local draft upon successful push
                localDraftDao.deleteDraft(repoId, path)
                
                // Update file cache in Room with new SHA (re-sync dir)
                val parentDir = path.substringBeforeLast("/", "")
                syncFiles(owner, repoName, repoId, parentDir, branch)
                Result.success(Unit)
            } else {
                Result.failure(Exception("GitHub Push Failed [${apiResponse.code()}]: ${apiResponse.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun commitAndDeleteFile(
        owner: String,
        repoName: String,
        repoId: Long,
        path: String,
        branch: String,
        commitMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getToken()
            if (token.isEmpty()) return@withContext Result.failure(Exception("Not authenticated"))

            val sha = cachedFileDao.getByPath(repoId, path)?.sha
                ?: return@withContext Result.failure(Exception("File details missing from cache - cannot delete on remote"))

            val body = GithubFileDeleteRequest(
                message = commitMessage,
                sha = sha,
                branch = branch
            )

            val apiResponse = RetrofitClient.githubService.deleteFile(
                tokenHeader = "token $token",
                owner = owner,
                repo = repoName,
                path = path,
                body = body
            )

            if (apiResponse.isSuccessful) {
                cachedFileDao.deleteFileById("$repoId/$path")
                localDraftDao.deleteDraft(repoId, path)
                Result.success(Unit)
            } else {
                Result.failure(Exception("GitHub delete failed: ${apiResponse.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}