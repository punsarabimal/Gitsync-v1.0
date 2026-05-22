package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.ClonedRepo
import com.example.data.GithubRepo
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.GitViewModel
import com.example.ui.viewmodel.RemoteReposUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: GitViewModel) {
    var activeTab by remember { mutableStateOf(0) } // 0: Repos, 1: Bookmarks, 2: Settings
    val clonedRepos by viewModel.clonedRepos.collectAsState()
    val authUiState by viewModel.authUiState.collectAsState()
    
    // Remote Repos clone view state
    var showCloneDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = when (activeTab) {
                            0 -> "Repositories"
                            1 -> "Workspace Tools"
                            else -> "Client Config"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    if (authUiState is AuthUiState.Success) {
                        val user = (authUiState as AuthUiState.Success).user
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF6366F1), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color(0xFF030712),
                    scrolledContainerColor = Color(0xFF030712),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Repos") },
                    label = { Text("Repos") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF6366F1),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFF6366F1),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF6366F1).copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmarks") },
                    label = { Text("Bookmarks") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF6366F1),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFF6366F1),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF6366F1).copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Config") },
                    label = { Text("Config") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF6366F1),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFF6366F1),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF6366F1).copy(alpha = 0.15f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.fetchRemoteRepos()
                        showCloneDialog = true
                    },
                    containerColor = Color(0xFF4F46E5), // Indigo 600
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                    text = { Text("Clone Remote Repo", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712))
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = ""
            ) { tab ->
                when (tab) {
                    0 -> ReposTab(clonedRepos, viewModel)
                    1 -> BookmarksTab(viewModel)
                    2 -> SettingsScreen(viewModel)
                }
            }
        }
    }

    if (showCloneDialog) {
        RemoteCloneDialog(
            viewModel = viewModel,
            onDismiss = { showCloneDialog = false }
        )
    }
}

@Composable
fun ReposTab(clonedRepos: List<ClonedRepo>, viewModel: GitViewModel) {
    if (clonedRepos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = Color(0xFF1E293B),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Working Repositories Available",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap 'Clone Remote Repo' to clone either public or private repositories secure on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(clonedRepos) { repo ->
                RepoCard(repo = repo, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoCard(repo: ClonedRepo, viewModel: GitViewModel) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.navigateToRepo(repo) }
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = if (repo.isPrivate) Color(0xFFF59E0B) else Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { expandedMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color(0xFF94A3B8))
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            modifier = Modifier.background(Color(0xFF0B0F19)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Remove local copy", color = Color.White) },
                                onClick = {
                                    viewModel.deleteRepo(repo.id)
                                    expandedMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )

                if (!repo.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Branch indicator
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(12.dp))
                            Text(
                                text = repo.selectedBranch,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF818CF8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Private or Public Badge
                    Text(
                        text = if (repo.isPrivate) "Private" else "Public",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (repo.isPrivate) Color(0xFFF59E0B) else Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarksTab(viewModel: GitViewModel) {
    val favorites by viewModel.favoriteFiles.collectAsState()
    val recents by viewModel.recentFiles.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bookmarks header
        item {
            Text(
                text = "Favorite Workspace Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (favorites.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E12)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "No pinned favorites yet. Pin code files inside your repos for instant access.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(favorites) { file ->
                FileListItemSquare(file = file, viewModel = viewModel)
            }
        }

        // Recent edits
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Recent Opened Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (recents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E12)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Files opened recently will appear here.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(recents) { file ->
                FileListItemSquare(file = file, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FileListItemSquare(file: com.example.data.CachedFile, viewModel: GitViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.openFile(file) }
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            IconButton(
                onClick = { viewModel.toggleFavorite(file) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (file.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = if (file.isFavorite) Color(0xFFF77E3C) else Color(0xFF8E8E93)
                )
            }
        }
    }
}

// Dialog for remote Repository Cloning
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteCloneDialog(viewModel: GitViewModel, onDismiss: () -> Unit) {
    val remoteUiState by viewModel.remoteReposUiState.collectAsState()
    var searchFilter by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clone Repository",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Filter Search Bar
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Filter repositories...", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF6366F1)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF1E293B)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scroll content
                Box(modifier = Modifier.weight(1f)) {
                    when (val state = remoteUiState) {
                        is RemoteReposUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Fetching your GitHub repos...", color = Color.White)
                                }
                            }
                        }
                        is RemoteReposUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.fetchRemoteRepos() }) {
                                        Text("Retry Fetch")
                                    }
                                }
                            }
                        }
                        is RemoteReposUiState.Success -> {
                            val filtered = state.repos.filter {
                                it.name.contains(searchFilter, ignoreCase = true) ||
                                        (it.description ?: "").contains(searchFilter, ignoreCase = true)
                            }

                            if (filtered.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No matching repositories found", color = Color(0xFF8E8E93))
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filtered) { repo ->
                                        RemoteRepoRow(repo = repo) {
                                            viewModel.cloneRepo(repo)
                                            onDismiss()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteRepoRow(repo: GithubRepo, onCloneClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCloneClick() }
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (repo.private) Color(0xFFF59E0B) else Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!repo.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Owner: ${repo.owner.login}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF818CF8)
                )
            }

            Button(
                onClick = onCloneClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981), // Emerald 500
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clone", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
