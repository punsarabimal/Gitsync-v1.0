package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FileContentUiState
import com.example.ui.viewmodel.GitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: GitViewModel) {
    val file by viewModel.selectedFile.collectAsState()
    val repo by viewModel.selectedRepo.collectAsState()
    val contentState by viewModel.activeFileContentState.collectAsState()

    var text by remember { mutableStateOf("") }
    var showSearchReplace by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showCommitDialog by remember { mutableStateOf(false) }

    // Synchronize loaded content state
    LaunchedEffect(contentState) {
        if (contentState is FileContentUiState.Success) {
            text = (contentState as FileContentUiState.Success).content
        }
    }

    if (file == null || repo == null) return

    val activeFile = file!!
    val activeRepo = repo!!

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeFile.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = activeFile.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Manual Save Draft
                    IconButton(onClick = {
                        viewModel.saveDraft(text)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Draft", tint = Color.White)
                    }

                    // Toggle Find-Replace
                    IconButton(onClick = { showSearchReplace = !showSearchReplace }) {
                        Icon(Icons.Default.FindInPage, contentDescription = "Search & Replace", tint = Color.White)
                    }

                    // Discard changes / Discard draft if modified
                    IconButton(onClick = { viewModel.discardActiveDraft() }) {
                        Icon(Icons.Default.Undo, contentDescription = "Reset Changes", tint = MaterialTheme.colorScheme.error)
                    }

                    // Commit and Push
                    Button(
                        onClick = { showCommitDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Emerald 500
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Push", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF030712))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF030712))
        ) {
            // Find and Replace panel
            AnimatedVisibility(visible = showSearchReplace) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Find text...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedBorderColor = Color(0xFF1E293B)
                                )
                            )
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                label = { Text("Replace with...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedBorderColor = Color(0xFF1E293B)
                                )
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        text = text.replace(searchQuery, replaceQuery)
                                        viewModel.saveDraft(text)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Text("Replace All", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    replaceQuery = ""
                                    showSearchReplace = false
                                },
                                modifier = Modifier.weight(0.5f)
                            ) {
                                Text("Dismiss", color = Color.White)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = contentState) {
                    is FileContentUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is FileContentUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(state.message, color = Color.White, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.openFile(activeFile) }) {
                                    Text("Retry Loading")
                                }
                            }
                        }
                    }
                    is FileContentUiState.Success, is FileContentUiState.Idle -> {
                        CodeEditorField(
                            value = text,
                            onValueChange = {
                                text = it
                                // Auto-save draft dynamically
                                viewModel.saveDraft(it)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCommitDialog) {
        var commitMessage by remember { mutableStateOf("Update ${activeFile.name}") }
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit and Push to GitHub", color = Color.White) },
            text = {
                Column {
                    Text("Provide a git message to commit changes on the ${activeRepo.selectedBranch} branch.", color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
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
                        viewModel.commitAndPushActiveFile(commitMessage) {
                            showCommitDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)) // Emerald 500
                ) {
                    Text("Push changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF0B0F19),
            modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(28.dp))
        )
    }
}

@Composable
fun CodeEditorField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val totalLines = remember(value) { value.lines().size }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .verticalScroll(scrollState)
    ) {
        // Line numbering left margin sidebar
        Column(
            modifier = Modifier
                .width(42.dp)
                .background(Color(0xFF0B0F19))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (idx in 1..totalLines) {
                Text(
                    text = idx.toString(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, bottom = 2.dp) // Adjusted height matching text line height spacing
                )
            }
        }

        VerticalDivider(color = Color(0xFF1E293B))

        // Code Input Field with Syntax Highlighter Transformation
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize(),
            textStyle = TextStyle(
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false
            ),
            visualTransformation = SyntaxHighlighterTransformation()
        )
    }
}

// Built-in Pure-Regex Dynamic Syntax Highlighter
class SyntaxHighlighterTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        val plainText = text.text
        builder.append(plainText)

        // 1. Highlight Kotlin, Java, JSON & C++ keywords
        val keywords = listOf(
            "package", "import", "class", "interface", "object", "fun", "val", "var",
            "return", "if", "else", "this", "super", "private", "public", "protected",
            "internal", "override", "null", "true", "false", "for", "while", "const",
            "when", "try", "catch", "throw", "companion", "data", "sealed"
        )
        for (word in keywords) {
            val pattern = "\\b$word\\b".toRegex()
            for (match in pattern.findAll(plainText)) {
                builder.addStyle(
                    style = SpanStyle(color = Color(0xFF818CF8), fontWeight = FontWeight.Bold),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }

        // 2. Highlighting annotation fields (e.g. @Composable, @Entity)
        val annotationPattern = "@\\w+".toRegex()
        for (match in annotationPattern.findAll(plainText)) {
            builder.addStyle(
                style = SpanStyle(color = Color(0xFFC7D2FE)),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 3. Highlight numbers
        val numberPattern = "\\b\\d+\\b".toRegex()
        for (match in numberPattern.findAll(plainText)) {
            builder.addStyle(
                style = SpanStyle(color = Color(0xFFF59E0B)),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 4. Highlight string values
        val stringPattern = "\"[^\"]*\"".toRegex()
        for (match in stringPattern.findAll(plainText)) {
            builder.addStyle(
                style = SpanStyle(color = Color(0xFF10B981)),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 5. Highlight single-line comments
        val commentPattern = "//.*".toRegex()
        for (match in commentPattern.findAll(plainText)) {
            builder.addStyle(
                style = SpanStyle(color = Color(0xFF64748B)),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
