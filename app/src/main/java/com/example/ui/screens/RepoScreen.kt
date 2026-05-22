package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CachedFile
import com.example.data.ClonedRepo
import com.example.ui.viewmodel.GitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(viewModel: GitViewModel) {
    val repo by viewModel.selectedRepo.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val allFiles by viewModel.repoFiles.collectAsState()
    val localDrafts by viewModel.localDrafts.collectAsState()
    val searchQuery by viewModel.fileSearchQuery.collectAsState()

    // Dialog state to create file/folder
    var showCreateDialog by remember { mutableStateOf(false) }

    // Intercept hardware/system back presses to navigate up directories first
    BackHandler(enabled = true) {
        val wentBack = viewModel.navigateBackDir()
        if (!wentBack) {
            viewModel.navigateBack()
        }
    }

    if (repo == null) return

    val displayedRepo = repo!!

    // Filter files for current directory only, or filter globally if search is active
    val filteredFiles = remember(allFiles, currentPath, searchQuery) {
        val list = if (searchQuery.isNotEmpty()) {
            allFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            allFiles.filter { file ->
                val fileParent = file.path.substringBeforeLast("/", "")
                fileParent == currentPath
            }
        }
        list.sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() }))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)),
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF030712))) {
                // Main top header
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = displayedRepo.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallSplit,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = displayedRepo.selectedBranch,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF818CF8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            val wentBack = viewModel.navigateBackDir()
                            if (!wentBack) {
                                viewModel.navigateBack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.syncDirectory(currentPath) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Cache", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF030712))
                )

                // High contrast directory breadcrumbs line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                    Text(
                        text = "root",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentPath.isEmpty()) Color.White else Color(0xFF64748B),
                        fontWeight = if (currentPath.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { if (currentPath.isNotEmpty()) viewModel.navigateToDir("") }
                    )
                    
                    if (currentPath.isNotEmpty()) {
                        val pathParts = currentPath.split("/")
                        var accumulatedPath = ""
                        
                        for (part in pathParts) {
                            accumulatedPath = if (accumulatedPath.isEmpty()) part else "$accumulatedPath/$part"
                            val copyAccPath = accumulatedPath
                            
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(12.dp))
                            Text(
                                text = part,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentPath == copyAccPath) Color.White else Color(0xFF64748B),
                                fontWeight = if (currentPath == copyAccPath) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    if (currentPath != copyAccPath) {
                                        viewModel.navigateToDir(copyAccPath)
                                    }
                                }
                            )
                        }
                    }
                }

                // Inline quick filter search box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(48.dp),
                    placeholder = { Text("Search files in repo...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF6366F1)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF1E293B)
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF10B981), // Emerald 500
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create New...", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF030712))
        ) {
            if (filteredFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(72.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No files found for search" else "No matching files in workspace cache",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = { viewModel.syncDirectory(currentPath) }) {
                            Text("Fetch remote directory files", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredFiles) { file ->
                        val hasDraft = localDrafts.any { it.path == file.path }
                        FileRowItem(
                            file = file,
                            hasLocalDraft = hasDraft,
                            onItemClick = {
                                if (file.type == "dir") {
                                    viewModel.navigateToDir(file.path)
                                } else {
                                    viewModel.openFile(file)
                                }
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, isFolder ->
                viewModel.createNewItem(name, isFolder)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun FileRowItem(
    file: CachedFile,
    hasLocalDraft: Boolean,
    onItemClick: () -> Unit,
    viewModel: GitViewModel
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onItemClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Folder vs File styled icons
                Icon(
                    imageVector = if (file.type == "dir") Icons.Default.Folder else Icons.Default.Article,
                    contentDescription = null,
                    tint = if (file.type == "dir") Color(0xFF6366F1) else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (hasLocalDraft) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Modified",
                                    fontSize = 9.sp,
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    if (file.size > 0L) {
                        Text(
                            text = "${file.size} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (file.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp).padding(end = 8.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color(0xFF94A3B8))
                    }
                    
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF0B0F19)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bookmark / Favorite", color = Color.White) },
                            onClick = {
                                viewModel.toggleFavorite(file)
                                dropdownExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename Locally", color = Color.White) },
                            onClick = {
                                showRenameDialog = true
                                dropdownExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete locally (Keep Remote)", color = Color.White) },
                            onClick = {
                                viewModel.deleteItemLocal(file)
                                dropdownExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.LightGray) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete On-GitHub Remote", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showDeleteConfirmDialog = true
                                dropdownExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
        
        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(start = 54.dp))
    }

    if (showRenameDialog) {
        RenameFileDialog(
            initialName = file.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.renameItemLocal(file, newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog) {
        var gitCommitMessage by remember { mutableStateOf("Delete ${file.name}") }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Commit and Delete Remote File", color = Color.White) },
            text = {
                Column {
                    Text("This operation physically deletes the file from the remote GitHub branch. Input your Git commit message:", color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = gitCommitMessage,
                        onValueChange = { gitCommitMessage = it },
                        label = { Text("Commit message") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF1E293B)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitRemoteDelete(file, gitCommitMessage)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete On GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF0B0F19),
            modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(28.dp))
        )
    }
}

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, isFolder: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isFolder by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Workspace Item", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g., config.json / api)") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF1E293B)
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isFolder,
                            onClick = { isFolder = false },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6366F1))
                        )
                        Text("File", color = Color.White)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isFolder,
                            onClick = { isFolder = true },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6366F1))
                        )
                        Text("Folder", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty()) onCreate(name, isFolder) },
                enabled = name.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF0B0F19),
        modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(28.dp))
    )
}

@Composable
fun RenameFileDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Workspace Item", color = Color.White) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFF1E293B)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty()) onRename(name) },
                enabled = name.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF0B0F19),
        modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(28.dp))
    )
}
