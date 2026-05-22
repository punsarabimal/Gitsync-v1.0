package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val user: GithubUser) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

sealed interface DeviceFlowUiState {
    object Idle : DeviceFlowUiState
    object Loading : DeviceFlowUiState
    data class CodeReceived(val userCode: String, val verificationUri: String) : DeviceFlowUiState
    data class Success(val user: GithubUser) : DeviceFlowUiState
    data class Error(val message: String) : DeviceFlowUiState
}

sealed interface RemoteReposUiState {
    object Loading : RemoteReposUiState
    data class Success(val repos: List<GithubRepo>) : RemoteReposUiState
    data class Error(val message: String) : RemoteReposUiState
}

sealed interface FileContentUiState {
    object Idle : FileContentUiState
    object Loading : FileContentUiState
    data class Success(val content: String) : FileContentUiState
    data class Error(val message: String) : FileContentUiState
}

class GitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GitRepository(application)
    private val secureStorage = SecureStorage(application)

    // Routing State: "login", "pin_lock", "dashboard", "repo_detail", "editor", "settings"
    private val _currentScreen = MutableStateFlow("login")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Screen navigation stack for back actions
    private val screenHistory = mutableListOf<String>()

    // Auth states
    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _deviceFlowUiState = MutableStateFlow<DeviceFlowUiState>(DeviceFlowUiState.Idle)
    val deviceFlowUiState: StateFlow<DeviceFlowUiState> = _deviceFlowUiState.asStateFlow()

    // Local repositories
    val clonedRepos: StateFlow<List<ClonedRepo>> = repository.getClonedReposFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Remote repositories for clone dialog/screen
    private val _remoteReposUiState = MutableStateFlow<RemoteReposUiState>(RemoteReposUiState.Loading)
    val remoteReposUiState: StateFlow<RemoteReposUiState> = _remoteReposUiState.asStateFlow()

    // Current Repo Details Browsing
    private val _selectedRepo = MutableStateFlow<ClonedRepo?>(null)
    val selectedRepo: StateFlow<ClonedRepo?> = _selectedRepo.asStateFlow()

    private val _currentPath = MutableStateFlow("") // e.g. "", "src", "src/main"
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val pathHistory = mutableListOf<String>()

    // Cached files for current selected repo
    val repoFiles: StateFlow<List<CachedFile>> = _selectedRepo
        .flatMapLatest { repo ->
            if (repo != null) {
                repository.getCachedFilesFlow(repo.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites & Recents
    val favoriteFiles: StateFlow<List<CachedFile>> = repository.getFavoriteFilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFiles: StateFlow<List<CachedFile>> = repository.getRecentFilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active File Editor states
    private val _selectedFile = MutableStateFlow<CachedFile?>(null)
    val selectedFile: StateFlow<CachedFile?> = _selectedFile.asStateFlow()

    private val _activeFileContentState = MutableStateFlow<FileContentUiState>(FileContentUiState.Idle)
    val activeFileContentState: StateFlow<FileContentUiState> = _activeFileContentState.asStateFlow()

    // Local drafts checklist
    val localDrafts: StateFlow<List<LocalDraft>> = _selectedRepo
        .flatMapLatest { repo ->
            if (repo != null) {
                repository.getLocalDraftsFlow(repo.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query in file manager
    private val _fileSearchQuery = MutableStateFlow("")
    val fileSearchQuery: StateFlow<String> = _fileSearchQuery.asStateFlow()

    // Security PIN Lock
    private val _pinSetupEnabled = MutableStateFlow(false)
    val pinSetupEnabled: StateFlow<Boolean> = _pinSetupEnabled.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // UI actions status
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var deviceFlowJob: Job? = null

    init {
        // Run setup checks
        _pinSetupEnabled.value = secureStorage.isPinEnabled()
        val hasToken = repository.hasToken()
        
        if (secureStorage.isPinEnabled()) {
            _currentScreen.value = "pin_lock"
        } else if (hasToken) {
            _currentScreen.value = "dashboard"
            loadLocalProfile()
        } else {
            _currentScreen.value = "login"
        }
    }

    private fun loadLocalProfile() {
        val user = GithubUser(
            login = repository.getSavedUserLogin(),
            id = 0,
            avatarUrl = repository.getSavedUserAvatar(),
            name = repository.getSavedUserName(),
            email = repository.getSavedUserEmail()
        )
        _authUiState.value = AuthUiState.Success(user)
    }

    // --- Navigation Helper ---

    fun navigateTo(screen: String) {
        if (_currentScreen.value != screen) {
            screenHistory.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack() {
        if (screenHistory.isNotEmpty()) {
            _currentScreen.value = screenHistory.removeAt(screenHistory.size - 1)
        }
    }

    // --- Authentication ---

    fun loginWithToken(token: String) {
        _authUiState.value = AuthUiState.Loading
        repository.loginWithToken(
            token = token,
            onSuccess = { user ->
                _authUiState.value = AuthUiState.Success(user)
                showStatus("Login successful!")
                navigateTo("dashboard")
            },
            onError = { err ->
                _authUiState.value = AuthUiState.Error(err)
                showStatus(err)
            }
        )
    }

    fun startDeviceFlowAuth(clientId: String) {
        _deviceFlowUiState.value = DeviceFlowUiState.Loading
        viewModelScope.launch {
            repository.initiateDeviceFlow(clientId).onSuccess { deviceCode ->
                _deviceFlowUiState.value = DeviceFlowUiState.CodeReceived(
                    userCode = deviceCode.userCode,
                    verificationUri = deviceCode.verificationUri
                )
                
                // Start polling
                pollDeviceToken(clientId, deviceCode.interval)
            }.onFailure { err ->
                _deviceFlowUiState.value = DeviceFlowUiState.Error(err.message ?: "Failed to receive device code")
            }
        }
    }

    private fun pollDeviceToken(clientId: String, intervalSeconds: Int) {
        deviceFlowJob?.cancel()
        deviceFlowJob = viewModelScope.launch {
            val intervalMs = (if (intervalSeconds <= 0) 5 else intervalSeconds) * 1000L
            while (true) {
                delay(intervalMs)
                repository.pollDeviceFlowToken(clientId).onSuccess { user ->
                    _deviceFlowUiState.value = DeviceFlowUiState.Success(user)
                    _authUiState.value = AuthUiState.Success(user)
                    showStatus("Authenticated via device code!")
                    navigateTo("dashboard")
                    deviceFlowJob?.cancel()
                }.onFailure { err ->
                    // Log or handle terminal errors. Standard polling response "authorization_pending" is expected here
                    if (err.message != "authorization_pending") {
                        _deviceFlowUiState.value = DeviceFlowUiState.Error(err.message ?: "Failed auth code check")
                        deviceFlowJob?.cancel()
                    }
                }
            }
        }
    }

    fun cancelDeviceAuth() {
        deviceFlowJob?.cancel()
        _deviceFlowUiState.value = DeviceFlowUiState.Idle
    }

    fun logout() {
        repository.logout()
        _authUiState.value = AuthUiState.Idle
        _deviceFlowUiState.value = DeviceFlowUiState.Idle
        showStatus("Logged out successfully.")
        _currentScreen.value = "login"
    }

    // --- Repository Commands ---

    fun fetchRemoteRepos() {
        _remoteReposUiState.value = RemoteReposUiState.Loading
        viewModelScope.launch {
            repository.getRemoteRepos()
                .onSuccess { list ->
                    _remoteReposUiState.value = RemoteReposUiState.Success(list)
                }
                .onFailure { err ->
                    _remoteReposUiState.value = RemoteReposUiState.Error(err.message ?: "Unknown error")
                }
        }
    }

    fun cloneRepo(githubRepo: GithubRepo) {
        showStatus("Cloning ${githubRepo.name}...")
        viewModelScope.launch {
            repository.cloneRepository(githubRepo)
                .onSuccess { localRepo ->
                    showStatus("Cloned ${githubRepo.name} successfully!")
                    navigateToRepo(localRepo)
                }
                .onFailure { err ->
                    showStatus("Clone failed: ${err.message}")
                }
        }
    }

    fun deleteRepo(repoId: Long) {
        viewModelScope.launch {
            repository.deleteClonedRepo(repoId)
            showStatus("Removed repo copy locally.")
            if (_selectedRepo.value?.id == repoId) {
                _selectedRepo.value = null
                _currentPath.value = ""
                navigateBack()
            }
        }
    }

    fun navigateToRepo(repo: ClonedRepo) {
        _selectedRepo.value = repo
        _currentPath.value = ""
        pathHistory.clear()
        navigateTo("repo_detail")
        syncDirectory("")
    }

    // --- File Manager Commands ---

    fun updateSearchQuery(query: String) {
        _fileSearchQuery.value = query
    }

    fun navigateToDir(dirPath: String) {
        pathHistory.add(_currentPath.value)
        _currentPath.value = dirPath
        syncDirectory(dirPath)
    }

    fun navigateBackDir(): Boolean {
        if (pathHistory.isNotEmpty()) {
            _currentPath.value = pathHistory.removeAt(pathHistory.size - 1)
            syncDirectory(_currentPath.value)
            return true
        }
        return false
    }

    fun syncDirectory(path: String) {
        val repo = _selectedRepo.value ?: return
        viewModelScope.launch {
            repository.syncFiles(
                owner = repo.owner,
                repo = repo.name,
                repoId = repo.id,
                directoryPath = path,
                branch = repo.selectedBranch
            ).onFailure { err ->
                showStatus("Cache update offline: ${err.localizedMessage ?: "No network connection"}")
            }
        }
    }

    fun toggleFavorite(file: CachedFile) {
        viewModelScope.launch {
            repository.toggleFavorite(file.repoId, file.path)
        }
    }

    fun createNewItem(name: String, isFolder: Boolean) {
        val repo = _selectedRepo.value ?: return
        viewModelScope.launch {
            val type = if (isFolder) "dir" else "file"
            repository.createNewOfflineFile(repo.id, _currentPath.value, name, type)
            syncDirectory(_currentPath.value)
            showStatus("Created $name offline draft.")
        }
    }

    fun deleteItemLocal(file: CachedFile) {
        viewModelScope.launch {
            repository.renameDeleteOfflineFile(file.repoId, file.path, "DELETE")
            syncDirectory(_currentPath.value)
            showStatus("Deleted ${file.name} locally.")
        }
    }

    fun renameItemLocal(file: CachedFile, newName: String) {
        viewModelScope.launch {
            repository.renameDeleteOfflineFile(file.repoId, file.path, "RENAME", newName)
            syncDirectory(_currentPath.value)
            showStatus("Renamed to $newName locally.")
        }
    }

    // --- Code Editor Commands ---

    fun openFile(file: CachedFile) {
        _selectedFile.value = file
        _activeFileContentState.value = FileContentUiState.Loading
        navigateTo("editor")
        
        val repo = _selectedRepo.value ?: return
        viewModelScope.launch {
            repository.getFileContent(
                owner = repo.owner,
                repo = repo.name,
                repoId = repo.id,
                path = file.path,
                branch = repo.selectedBranch
            ).onSuccess { text ->
                _activeFileContentState.value = FileContentUiState.Success(text)
            }.onFailure { err ->
                _activeFileContentState.value = FileContentUiState.Error(err.message ?: "Failed to open file")
            }
        }
    }

    fun saveDraft(content: String) {
        val file = _selectedFile.value ?: return
        viewModelScope.launch {
            repository.saveDraft(file.repoId, file.path, content)
            // Refresh editor content state
            _activeFileContentState.value = FileContentUiState.Success(content)
        }
    }

    fun discardActiveDraft() {
        val file = _selectedFile.value ?: return
        viewModelScope.launch {
            repository.discardDraft(file.repoId, file.path)
            showStatus("Discarded draft changes.")
            // Re-open file to trigger standard remote load
            openFile(file)
        }
    }

    fun commitAndPushActiveFile(commitMessage: String, onSuccess: () -> Unit) {
        val file = _selectedFile.value ?: return
        val repo = _selectedRepo.value ?: return
        
        _activeFileContentState.value = FileContentUiState.Loading
        viewModelScope.launch {
            repository.commitAndPushFile(
                owner = repo.owner,
                repoName = repo.name,
                repoId = repo.id,
                path = file.path,
                branch = repo.selectedBranch,
                commitMessage = commitMessage
            ).onSuccess {
                showStatus("Committed and Pushed to GitHub!")
                _selectedFile.value?.let { openFile(it) } // Reload
                onSuccess()
            }.onFailure { err ->
                showStatus("Push error: ${err.message}")
                // Restore content
                val draft = repository.getLocalDraftsFlow(repo.id).first().firstOrNull { it.path == file.path }
                _activeFileContentState.value = FileContentUiState.Success(draft?.localContent ?: "")
            }
        }
    }

    fun submitRemoteDelete(file: CachedFile, commitMessage: String) {
        val repo = _selectedRepo.value ?: return
        showStatus("Deleting ${file.name} on remote GitHub...")
        viewModelScope.launch {
            repository.commitAndDeleteFile(
                owner = repo.owner,
                repoName = repo.name,
                repoId = repo.id,
                path = file.path,
                branch = repo.selectedBranch,
                commitMessage = commitMessage
            ).onSuccess {
                showStatus("Deleted successfully from GitHub repository!")
                syncDirectory(_currentPath.value)
            }.onFailure { err ->
                showStatus("Failed remote delete: ${err.message}")
            }
        }
    }

    // --- PIN Management ---

    fun setupPIN(pin: String) {
        secureStorage.savePin(pin)
        _pinSetupEnabled.value = true
        showStatus("PIN Lock established successfully.")
    }

    fun removePIN() {
        secureStorage.disablePin()
        _pinSetupEnabled.value = false
        showStatus("PIN deactivated.")
    }

    fun unlockWithPIN(pin: String): Boolean {
        return if (secureStorage.verifyPin(pin)) {
            _isAuthenticated.value = true
            val hasToken = repository.hasToken()
            if (hasToken) {
                _currentScreen.value = "dashboard"
                loadLocalProfile()
            } else {
                _currentScreen.value = "login"
            }
            true
        } else {
            showStatus("Incorrect Passcode!")
            false
        }
    }

    // --- Status Updates ---

    private fun showStatus(msg: String) {
        _statusMessage.value = msg
        viewModelScope.launch {
            delay(4000)
            if (_statusMessage.value == msg) {
                _statusMessage.value = null
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
