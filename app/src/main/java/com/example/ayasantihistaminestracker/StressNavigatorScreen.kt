package com.example.ayasantihistaminestracker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isReframe: Boolean = false,
    var isSaved: Boolean = false
)

data class MoodEntry(
    val timestamp: Long,
    val level: StressLevel
)

enum class UrtiEmotion {
    NEUTRAL, PANIC, ANXIETY, STRESS, BURNOUT, SADNESS, FRUSTRATION, FEAR, EXHAUSTION, OVERTHINKING, HOPELESSNESS, REFLECTIVE, FLARE_UP, CRISIS
}

enum class StressLevel(val label: String, val color: Color) {
    CALM("Calm", Color(0xFF66BB6A)),
    MILD("Mild Stress", Color(0xFFFFCA28)),
    STRESSED("Stressed", Color(0xFFFFA726)),
    OVERWHELMED("Overwhelmed", Color(0xFFFF7043)),
    VERY_HEAVY("Very Heavy", Color(0xFFEF5350))
}

enum class UserIntent(val label: String) {
    VENT("I just want to vent"),
    ADVICE("I want advice"),
    CALMING("I need calming"),
    NUMB("I feel numb"),
    UNSURE("I don't know")
}

class UrtiSessionState {
    var lastEmotion by mutableStateOf(UrtiEmotion.NEUTRAL)
    val mentionedTopics = mutableSetOf<String>()
    var stressLevel by mutableStateOf(StressLevel.CALM)
    var intent by mutableStateOf<UserIntent?>(null)
    var isBrainDumpMode by mutableStateOf(false)
    var isAmbientMode by mutableStateOf(false)
    val moodHistory = mutableStateListOf<MoodEntry>()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StressNavigatorScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("StressNavigator", android.content.Context.MODE_PRIVATE) }
    val gson = remember { com.google.gson.Gson() }
    
    var chatStarted by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var showContinuationDialog by remember { mutableStateOf(false) }
    var showSavedTools by remember { mutableStateOf(false) }
    var showExitCalmDialog by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isUrtiTyping by remember { mutableStateOf(false) }
    
    var activeExercise by remember { mutableStateOf<String?>(null) }
    
    val sessionState = remember { UrtiSessionState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = chatStarted && !exiting) {
        showExitCalmDialog = true
    }

    LaunchedEffect(sessionState.stressLevel) {
        sessionState.moodHistory.add(MoodEntry(System.currentTimeMillis(), sessionState.stressLevel))
        prefs.edit().putString("mood_history", gson.toJson(sessionState.moodHistory.toList())).apply()
    }

    LaunchedEffect(Unit) {
        val historyJson = prefs.getString("mood_history", "[]")
        val type = object : com.google.gson.reflect.TypeToken<List<MoodEntry>>() {}.type
        val savedMoods: List<MoodEntry> = gson.fromJson(historyJson, type)
        sessionState.moodHistory.clear()
        sessionState.moodHistory.addAll(savedMoods)
        
        val history = prefs.getString("chat_history", null)
        if (history != null) {
            val msgType = object : com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.type
            val savedMessages: List<ChatMessage> = gson.fromJson(history, msgType)
            if (savedMessages.isNotEmpty()) messages = savedMessages
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            prefs.edit().putString("chat_history", gson.toJson(messages)).apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Stress Navigator", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        AnimatedVisibility(visible = isUrtiTyping, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Text("Urti is thinking...", color = Color(0xFF82B1FF), fontSize = 12.sp, fontWeight = FontWeight.Normal)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (chatStarted) showExitCalmDialog = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { sessionState.isAmbientMode = !sessionState.isAmbientMode }) {
                        Icon(Icons.Default.Settings, "Ambient", tint = if (sessionState.isAmbientMode) Color(0xFF42A5F5) else Color.White)
                    }
                    if (chatStarted) {
                        IconButton(onClick = { showSavedTools = true }) {
                            Icon(Icons.Default.Favorite, "Saved", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (sessionState.isAmbientMode) Color(0xFF0A0C14) else Color(0xFF0F111A))
            )
        },
        containerColor = if (sessionState.isAmbientMode) Color(0xFF0A0C14) else Color(0xFF0F111A)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (sessionState.isAmbientMode) AmbientGlow(isIntense = false)

            if (!chatStarted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!sessionState.isAmbientMode) AmbientGlow()
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Image(
                            painter = painterResource(id = R.drawable.urti),
                            contentDescription = "Urti Guardian",
                            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text("Hi, I'm Urti.", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Your well-being guardian and this is your quiet space to find balance", color = Color.LightGray.copy(alpha = 0.7f), fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        StressLevelCheckIn(sessionState)
                        IntentSelector(sessionState)

                        Button(onClick = { 
                            val history = prefs.getString("chat_history", null)
                            if (history != null) showContinuationDialog = true else {
                                messages = listOf(
                                    ChatMessage(text = "Hi! I'm Urti, your Well-being Guardian.", isUser = false),
                                    ChatMessage(text = "This is your space to pause, vent, or simply breathe.", isUser = false)
                                )
                                chatStarted = true
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Talk to Urti", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Button(
                            onClick = { activeExercise = "Breathing Visualizer" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222533)),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
                        ) {
                            Text("Let’s Breathe", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = if (sessionState.isBrainDumpMode) Color(0xFF673AB7).copy(alpha = 0.1f) else sessionState.stressLevel.color.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (sessionState.isBrainDumpMode) Color(0xFF673AB7).copy(alpha = 0.3f) else sessionState.stressLevel.color.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (sessionState.isBrainDumpMode) "Brain Dump Mode" else "Current State: ", color = Color.Gray, fontSize = 12.sp)
                            if (!sessionState.isBrainDumpMode) Text(sessionState.stressLevel.label, color = sessionState.stressLevel.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { 
                                if (sessionState.isBrainDumpMode) {
                                    sessionState.isBrainDumpMode = false
                                    messages = messages + ChatMessage(text = "I'm here again. We can talk whenever you're ready.", isUser = false)
                                } else chatStarted = false 
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (sessionState.isBrainDumpMode) "Switch to Chat" else "Adjust", color = Color(0xFF82B1FF), fontSize = 11.sp)
                            }
                        }
                    }

                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(messages, key = { it.id }) { message ->
                            AnimatedChatMessage(message) {
                                messages = messages.map { if (it.id == message.id) it.copy(isSaved = !it.isSaved) else it }
                            }
                        }
                    }

                    QuickSupportSection(
                        onSelected = { selected ->
                            if (!isUrtiTyping) coroutineScope.launch { handleMessageSend(selected, messages, sessionState, { messages = it }, { isUrtiTyping = it }, listState) }
                        }
                    )

                    Surface(color = Color(0xFF161822), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                        Row(modifier = Modifier.padding(16.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.Bottom) {
                            TextField(value = inputText, onValueChange = { inputText = it }, placeholder = { Text("Speak with Urti...", color = Color.Gray, fontSize = 14.sp) }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF0F111A), unfocusedContainerColor = Color(0xFF0F111A), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color(0xFF42A5F5), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                if (inputText.isNotBlank() && !isUrtiTyping) {
                                    val msg = inputText
                                    inputText = ""
                                    coroutineScope.launch { handleMessageSend(msg, messages, sessionState, { messages = it }, { isUrtiTyping = it }, listState) }
                                }
                            }, enabled = inputText.isNotBlank() && !isUrtiTyping, modifier = Modifier.size(48.dp).background(if (inputText.isNotBlank() && !isUrtiTyping) Color(0xFF42A5F5) else Color(0xFF2C2C2C), RoundedCornerShape(24.dp))) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.Black)
                            }
                        }
                    }
                }
            }

            if (showSavedTools) SavedToolsView(messages) { showSavedTools = false }
            if (showExitCalmDialog) ExitCalmDialog(onExit = { exiting = true; onBack() }, onCancel = { showExitCalmDialog = false })
            
            if (showContinuationDialog) {
                AlertDialog(onDismissRequest = { showContinuationDialog = false }, title = { Text("Welcome back", color = Color.White) }, text = { Text("Continue your journey or start new?", color = Color.LightGray) }, containerColor = Color(0xFF1E1E1E),
                    confirmButton = { TextButton(onClick = {
                        if (messages.isNotEmpty()) {
                            messages = messages + listOf(
                                ChatMessage(text = "Hey again. How are things feeling right now?", isUser = false)
                            )
                        }
                        chatStarted = true
                        showContinuationDialog = false
                    }) { Text("Continue", color = Color(0xFF42A5F5)) } },
                    dismissButton = { TextButton(onClick = {
                        messages = listOf(
                            ChatMessage(text = "Hi! I’m Urti, your Well-being Guardian.", isUser = false),
                            ChatMessage(text = "This is your space to pause, vent, or simply breathe.", isUser = false)
                        )
                        prefs.edit().remove("chat_history").apply()
                        sessionState.moodHistory.clear()
                        prefs.edit().remove("mood_history").apply()
                        chatStarted = true
                        showContinuationDialog = false
                    }) { Text("Start New", color = Color.White) } }
                )
            }

            if (activeExercise != null) {
                if (activeExercise == "Breathing Visualizer") BreathingVisualOverlay { activeExercise = null }
                else if (activeExercise == "Brain Dump Mode") {
                    sessionState.isBrainDumpMode = true; chatStarted = true; activeExercise = null
                    messages = messages + ChatMessage(text = "Brain Dump Mode active. I'll listen quietly.", isUser = false, isReframe = true)
                } else CalmingExerciseOverlay(activeExercise!!) { activeExercise = null }
            }
        }
    }
}

@Composable
fun AnimatedChatMessage(message: ChatMessage, onToggleSave: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(800)) + slideInVertically(tween(800), initialOffsetY = { it / 4 })) {
        ChatBubble(message, onToggleSave)
    }
}

@Composable
fun AmbientGlow(isIntense: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (isIntense) 0.04f else 0.02f,
        targetValue = if (isIntense) 0.12f else 0.06f,
        animationSpec = infiniteRepeatable(animation = tween(if (isIntense) 5000 else 8000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )
    Canvas(modifier = Modifier.fillMaxSize().blur(100.dp)) {
        drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF42A5F5), Color.Transparent), center = center), radius = size.minDimension, alpha = alpha)
    }
}

@Composable
fun ExitCalmDialog(onExit: () -> Unit, onCancel: () -> Unit) {
    var showBreathing by remember { mutableStateOf(false) }
    if (showBreathing) BreathingVisualOverlay { onExit() }
    else AlertDialog(onDismissRequest = onCancel, title = { Text("Take a 10-second reset?", color = Color.White) }, confirmButton = { Button(onClick = { showBreathing = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))) { Text("Yes", color = Color.Black) } }, dismissButton = { TextButton(onClick = onExit) { Text("Exit now", color = Color.White.copy(alpha = 0.6f)) } }, containerColor = Color(0xFF1E1E1E))
}

@Composable
fun SavedToolsView(messages: List<ChatMessage>, onDismiss: () -> Unit) {
    val saved = messages.filter { it.isSaved }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Color(0xFF42A5F5)) } }, title = { Text("Saved Anchors", color = Color.White) }, containerColor = Color(0xFF0F111A), text = {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            if (saved.isEmpty()) item { Text("No saved messages.", color = Color.Gray) }
            else items(saved) { msg ->
                Surface(color = Color(0xFF1E212B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(msg.text, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }
        }
    })
}

@Composable
fun QuickSupportSection(onSelected: (String) -> Unit) {
    val chips = listOf("I'm stressed", "I can't sleep")
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(chips) { chip ->
            Surface(color = Color(0xFF222533), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)), modifier = Modifier.clickable {
                onSelected(chip)
            }) { Text(chip, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
        }
    }
}

suspend fun handleMessageSend(text: String, currentMessages: List<ChatMessage>, sessionState: UrtiSessionState, onMessagesChange: (List<ChatMessage>) -> Unit, onTypingChange: (Boolean) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState) {
    var updated = currentMessages + ChatMessage(text = text, isUser = true)
    onMessagesChange(updated)
    listState.animateScrollToItem(updated.size - 1)
    onTypingChange(true)
    delay(1000)
    if (sessionState.isBrainDumpMode) {
        onTypingChange(false)
        if (Math.random() > 0.7) {
            updated = updated + ChatMessage(text = listOf("This feeling is temporary.", "You don't need to solve everything now.", "Rest is valid.").random(), isUser = false, isReframe = true)
            onMessagesChange(updated)
        }
        return
    }
    val resp = UrtiBrain.process(text, sessionState)
    onTypingChange(false)
    val chunks = resp.split(". ")
    for (c in chunks) {
        updated = updated + ChatMessage(text = c, isUser = false)
        onMessagesChange(updated)
        listState.animateScrollToItem(updated.size - 1)
        delay(1000)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatMessage, onToggleSave: () -> Unit) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = when { message.isUser -> Color(0xFF42A5F5); message.isReframe -> Color(0xFF2E323D); else -> Color(0xFF1E212B) }
    Box(modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = { onToggleSave() }, onClick = {}), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp)) {
            if (!message.isUser) {
                Image(
                    painter = painterResource(id = R.drawable.urti),
                    contentDescription = "Urti",
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Surface(color = color, shape = RoundedCornerShape(22.dp, 22.dp, if(message.isUser) 4.dp else 22.dp, if(message.isUser) 22.dp else 4.dp), border = if(message.isReframe) BorderStroke(1.dp, Color(0xFF82B1FF).copy(alpha = 0.3f)) else null) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (message.isReframe) Text("✨ ", fontSize = 14.sp)
                    Text(message.text, color = if(message.isUser) Color.Black else Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                    if (message.isSaved) Icon(Icons.Default.Favorite, "", tint = Color.Red, modifier = Modifier.size(12.dp).padding(start = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentSelector(session: UrtiSessionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("What do you need right now?", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UserIntent.entries.forEach { intent ->
                val sel = session.intent == intent
                Surface(color = if(sel) Color(0xFF42A5F5) else Color(0xFF222533), shape = RoundedCornerShape(20.dp), modifier = Modifier.clickable { session.intent = intent }) {
                    Text(intent.label, color = if(sel) Color.Black else Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun BreathingVisualOverlay(onDismiss: () -> Unit) {
    var phase by remember { mutableStateOf("Inhale") }
    val scale by rememberInfiniteTransition().animateFloat(0.8f, 1.5f, animationSpec = infiniteRepeatable(keyframes { durationMillis = 12000; 0.8f at 0; 1.5f at 4000; 1.5f at 8000; 0.8f at 12000 }, RepeatMode.Restart))
    LaunchedEffect(Unit) { while(true) { phase = "Inhale"; delay(4000); phase = "Hold"; delay(4000); phase = "Exhale"; delay(4000) } }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Better", color = Color(0xFF42A5F5)) } }, containerColor = Color(0xFF0F111A), text = {
        Column(modifier = Modifier.fillMaxWidth().height(300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(80.dp).graphicsLayer(scaleX = scale, scaleY = scale), shape = RoundedCornerShape(40.dp), color = Color(0xFF42A5F5).copy(alpha = 0.6f)) {}
                Text(phase, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Follow the rhythm.", color = Color.Gray)
        }
    })
}

@Composable
fun StressLevelCheckIn(session: UrtiSessionState) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E212B)), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("How heavy is your mind now?", color = Color.White, fontSize = 15.sp)
            Slider(value = session.stressLevel.ordinal.toFloat(), onValueChange = { session.stressLevel = StressLevel.entries[it.toInt()] }, valueRange = 0f..4f, steps = 3)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.stressLevel.label, color = session.stressLevel.color, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CalmingExerciseOverlay(exercise: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Color(0xFF42A5F5)) } }, title = { Text(exercise, color = Color.White) }, text = {
        Text(when(exercise) {
            "Grounding" -> "5-4-3-2-1 Grounding: 5 things you see, 4 feel, 3 hear, 2 smell, 1 taste."
            "Calm me down" -> "Close your eyes. Visualize a calm place for 30 seconds."
            else -> "Take a deep breath."
        }, color = Color.LightGray)
    }, containerColor = Color(0xFF1E1E1E))
}

object UrtiBrain {
    fun process(input: String, session: UrtiSessionState): String {
        val query = input.lowercase()

        // 1. Safety Boundary Check
        if (isCrisis(query)) {
            return "I hear how much pain you're in, and I'm here with you. Please reach out to a professional or a crisis line right now. Your safety and your life are incredibly important. You don't have to carry this alone."
        }

        // 2. Emotional and State Detection
        val detectedEmotion = detectEmotion(query)
        session.lastEmotion = detectedEmotion

        if (query.contains("sleep")) session.mentionedTopics.add("sleep")
        if (query.contains("flare")) session.mentionedTopics.add("flare-ups")

        // 3. Flare-Up Mode
        if (isFlareUp(query)) {
            return handleFlareUp(query)
        }

        // 4. Intent Adaptation
        val intentPrefix = when(session.intent) {
            UserIntent.VENT -> "I'm listening closely. Let it out. "
            UserIntent.ADVICE -> "I'll try to offer some perspective. "
            UserIntent.CALMING -> "Let's find some quiet now. "
            else -> ""
        }

        // 5. Calm first. Solutions second.
        val baseResponse = when (detectedEmotion) {
            UrtiEmotion.PANIC -> "I'm right here with you. You're safe right now. Let's slow this down together. One slower breath... focus on the feeling of your feet on the ground."
            UrtiEmotion.ANXIETY -> "I hear how much is on your mind. It's okay to feel uneasy. Your nervous system is just trying to protect you, but we can tell it that it's okay to rest for a moment."
            UrtiEmotion.BURNOUT, UrtiEmotion.EXHAUSTION -> "You sound emotionally exhausted. It's okay to just exist without being productive. Let yourself have permission to rest."
            UrtiEmotion.SADNESS, UrtiEmotion.HOPELESSNESS -> "That sounds really heavy. You don't need to solve everything tonight. I'm listening, and I'm here in the quiet with you."
            UrtiEmotion.FRUSTRATION -> "It makes sense that you're frustrated. It's valid to feel this way. Let's just sit with that feeling for a second without trying to fix it."
            UrtiEmotion.REFLECTIVE -> "It's brave to look inward like this. I'm curious, how does that realization feel in your body right now?"
            else -> getNeutralResponse(query, session)
        }

        return intentPrefix + baseResponse
    }

    private fun isCrisis(query: String): Boolean {
        val keywords = listOf("suicide", "kill myself", "end it all", "self harm", "hurt myself")
        return keywords.any { query.contains(it) }
    }

    private fun isFlareUp(query: String): Boolean {
        val keywords = listOf("flaring", "burning", "itchy", "hives", "flare-up", "skin is on fire")
        return keywords.any { query.contains(it) }
    }

    private fun handleFlareUp(query: String): String {
        return listOf(
            "Okay. Stay with me for a moment. Unclench your shoulders if you can. One slower breath. You do not need to solve tomorrow right now.",
            "I hear you. Your body is feeling a lot right now. Let's focus on this exact moment. Breathe in slowly... and out. You're safe.",
            "I'm sorry it's so uncomfortable right now. Let's try to find a tiny bit of softness. Relax your jaw just a little. I'm right here."
        ).random()
    }

    private fun detectEmotion(query: String): UrtiEmotion {
        return when {
            query.contains("panic") || query.contains("can't breathe") || query.contains("dying") -> UrtiEmotion.PANIC
            query.contains("anxious") || query.contains("anxiety") || query.contains("fear") || query.contains("scared") -> UrtiEmotion.ANXIETY
            query.contains("burnout") || query.contains("done") || query.contains("can't do this") -> UrtiEmotion.BURNOUT
            query.contains("exhausted") || query.contains("tired") || query.contains("no energy") -> UrtiEmotion.EXHAUSTION
            query.contains("sad") || query.contains("lonely") || query.contains("crying") -> UrtiEmotion.SADNESS
            query.contains("frustrated") || query.contains("angry") || query.contains("hate this") -> UrtiEmotion.FRUSTRATION
            query.contains("why") || query.contains("notice") || query.contains("think") || query.contains("realize") -> UrtiEmotion.REFLECTIVE
            else -> UrtiEmotion.NEUTRAL
        }
    }

    private fun getNeutralResponse(query: String, session: UrtiSessionState): String {
        if (query.contains("hello") || query.contains("hi")) return "Hello. I'm here for you. How are things feeling right now?"
        if (query.contains("thank")) return "You're so welcome. It's an honor to support you."
        
        return listOf(
            "I hear you. Tell me more.",
            "I understand. I'm listening.",
            "Your thoughts are safe with me.",
            "Let's take this one moment at a time.",
            "What feels hardest in this moment?"
        ).random()
    }
}
