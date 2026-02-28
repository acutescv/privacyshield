package com.privacyshield.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.privacyshield.llm.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val context    = LocalContext.current
    val uiState    by viewModel.uiState.collectAsState()
    val listState  = rememberLazyListState()
    val keyboard   = LocalSoftwareKeyboardController.current
    var inputText  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.init(context) }

    // Auto-scroll on new content
    LaunchedEffect(uiState.messages.size, uiState.streamingText.length) {
        val count = uiState.messages.size + if (uiState.isGenerating) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Privacy Assistant")
                        Text(
                            text = when (uiState.modelStatus) {
                                ModelStatus.NOT_LOADED -> "Loading model…"
                                ModelStatus.LOADING    -> "Loading model…"
                                ModelStatus.READY      -> "Ready · Offline"
                                ModelStatus.ERROR      -> "Model error"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (uiState.modelStatus) {
                                ModelStatus.READY  -> MaterialTheme.colorScheme.primary
                                ModelStatus.ERROR  -> MaterialTheme.colorScheme.error
                                else               -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetChat) {
                        Icon(Icons.Default.Refresh, "Reset chat")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Error banner ───────────────────────────────────────────────
            uiState.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Model error: $err\n\nPlace your .gguf file in:\nAndroid/data/com.privacyshield/files/models/",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Messages ───────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Welcome message
                if (uiState.messages.isEmpty() && uiState.modelStatus == ModelStatus.READY) {
                    item {
                        WelcomeCard()
                    }
                    item {
                        SuggestedPromptsRow(
                            prompts = viewModel.suggestedPrompts,
                            onSelect = {
                                viewModel.sendMessage(it)
                                keyboard?.hide()
                            }
                        )
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }

                // Streaming
                if (uiState.isGenerating) {
                    item {
                        if (uiState.streamingText.isNotEmpty()) {
                            MessageBubble(
                                Message(content = uiState.streamingText, isUser = false),
                                isStreaming = true
                            )
                        } else {
                            TypingIndicator()
                        }
                    }
                }
            }

            // ── Input row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Ask about privacy…") },
                    maxLines      = 5,
                    enabled       = uiState.modelStatus == ModelStatus.READY,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        keyboard?.hide()
                    })
                )

                if (uiState.isGenerating) {
                    IconButton(onClick = viewModel::stopGeneration) {
                        Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            keyboard?.hide()
                        },
                        enabled = inputText.isNotBlank() && uiState.modelStatus == ModelStatus.READY
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isStreaming: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 290.dp),
            shape = RoundedCornerShape(
                topStart    = 18.dp,
                topEnd      = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd   = if (message.isUser) 4.dp  else 18.dp
            ),
            color = if (message.isUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Text(
                text     = message.content + if (isStreaming) "▌" else "",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color    = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ), label = "dot$i"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
fun WelcomeCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🔒 Privacy Assistant", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ask me anything about ID card privacy, safe sharing, or identity protection. " +
                "All conversations stay on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SuggestedPromptsRow(prompts: List<String>, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(prompts) { prompt ->
            SuggestionChip(
                onClick = { onSelect(prompt) },
                label   = { Text(prompt, maxLines = 2) }
            )
        }
    }
}
