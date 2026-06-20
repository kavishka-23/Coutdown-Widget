package com.example.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CountdownEvent
import com.example.CountdownWidgetProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import androidx.compose.ui.res.painterResource
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import java.util.*

@Composable
fun CountdownApp(viewModel: CountdownViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val pinnedEvent by viewModel.pinnedEvent.collectAsState()

    // Notification permission launcher for Android 13 (Tiramisu)+ milestone alerts
    val notiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Milestone notifications are currently disabled. You can enable them anytime in App Settings.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notiPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Active bottom navigation tab: 0 = Countdown, 1 = Calendar, 2 = Settings
    var activeTab by remember { mutableStateOf(0) }

    // Dialog controllers
    var showAddDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<CountdownEvent?>(null) }

    // Fallback pinned event if none explicitly pinned
    val activePinnedEvent = pinnedEvent ?: allEvents.firstOrNull()

    // Background themes - light mode sky blue mix
    val baseBgColor = if (isDarkMode) Color(0xFF050505) else Color(0xFFE0F2FE)

    // Trigger widget updates whenever events or pinned state changes
    LaunchedEffect(allEvents, activePinnedEvent) {
        CountdownWidgetProvider.triggerWidgetUpdate(context)
    }

    val confettiState = remember { mutableStateListOf<ConfettiParticle>() }

    // Monitor for newly concluded countdowns to pop interactive confetti!
    LaunchedEffect(allEvents, currentTime) {
        val sharedPrefs = context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var needBurst = false
        for (event in allEvents) {
            val hasEnded = event.targetTimestamp <= now
            if (hasEnded) {
                val confettiKey = "confetti_shown_${event.id}"
                if (!sharedPrefs.getBoolean(confettiKey, false)) {
                    sharedPrefs.edit().putBoolean(confettiKey, true).apply()
                    needBurst = true
                }
            }
        }
        if (needBurst) {
            triggerConfettiBurst(confettiState)
        }
    }

    // Confetti Physics loop carrying genuine upward blasting and downward drifting under gravity and wind sways
    LaunchedEffect(confettiState.size) {
        if (confettiState.isNotEmpty()) {
            var frames = 0
            while (confettiState.isNotEmpty()) {
                delay(16)
                frames++
                val updated = confettiState.map { p ->
                    // Apply gravity force (accelerating downward)
                    val nextSpeedY = p.speedY + p.gravity
                    val newY = p.y + nextSpeedY
                    
                    // Wind sway simulation based on sinusoidal wave
                    val windForce = 0.0006f * kotlin.math.sin(frames * 0.08f + p.id)
                    val newX = (p.x + p.speedX + windForce).coerceIn(0f, 1f)
                    
                    // Fade out once falling back down past lower part of screen
                    val newOpacity = if (newY > 0.85f && nextSpeedY > 0) {
                        (p.opacity - 0.015f).coerceAtLeast(0f)
                    } else {
                        p.opacity
                    }
                    
                    p.copy(
                        y = newY,
                        x = newX,
                        speedY = nextSpeedY,
                        rotation = p.rotation + p.rotationSpeed,
                        opacity = newOpacity
                    )
                }.filter { it.opacity > 0f && it.y < 1.15f }

                confettiState.clear()
                confettiState.addAll(updated)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        // Aesthetic Gradient Glow Blobs behind the screen
        GalaxyBlobBackground(isDarkMode = isDarkMode)

        // Seasonal ambient overlay effects (snow list, cherry blossoms falling, sun light shining, maple drift)
        SeasonalAmbientOverlay(isDarkMode = isDarkMode)

        // Scaffold styled container
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                CustomBottomNavBar(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    isDarkMode = isDarkMode,
                    modifier = Modifier.padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    0 -> CountdownHomeView(
                        activePinnedEvent = activePinnedEvent,
                        allEvents = allEvents,
                        currentTime = currentTime,
                        isDarkMode = isDarkMode,
                        onAddClick = { showAddDialog = true },
                        onPinClick = { viewModel.selectPinnedEvent(it) },
                        onEditClick = { eventToEdit = it },
                        onDeleteClick = { viewModel.deleteEvent(it) },
                        onToggleTheme = { viewModel.toggleDarkMode() }
                    )
                    1 -> CalendarEventsView(
                        allEvents = allEvents,
                        currentTime = currentTime,
                        isDarkMode = isDarkMode,
                        onAddClick = { showAddDialog = true },
                        onPinClick = { viewModel.selectPinnedEvent(it) },
                        onEditClick = { eventToEdit = it },
                        onDeleteClick = { viewModel.deleteEvent(it) }
                    )
                    2 -> SettingsAndInfoView(
                        allEventsCount = allEvents.size,
                        isDarkMode = isDarkMode,
                        onToggleTheme = { viewModel.toggleDarkMode() },
                        onTriggerWidgetSync = {
                            CountdownWidgetProvider.triggerWidgetUpdate(context)
                            Toast.makeText(context, "Homescreen Widget Synced!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Add Dialog
        if (showAddDialog) {
            GlassyEventDialog(
                isEditMode = false,
                isDarkMode = isDarkMode,
                onDismiss = { showAddDialog = false },
                onSave = { title, category, timestamp, isRepeating, repeatStartDate, repeatEndDate, repeatDayOfWeek, repeatHour, repeatMinute, repeatAmPm ->
                    viewModel.addEvent(
                        title = title,
                        category = category,
                        targetTimestamp = timestamp,
                        isRepeating = isRepeating,
                        repeatStartDate = repeatStartDate,
                        repeatEndDate = repeatEndDate,
                        repeatDayOfWeek = repeatDayOfWeek,
                        repeatHour = repeatHour,
                        repeatMinute = repeatMinute,
                        repeatAmPm = repeatAmPm
                    )
                    showAddDialog = false
                    Toast.makeText(context, "Event Added!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Dialog
        if (eventToEdit != null) {
            GlassyEventDialog(
                isEditMode = true,
                initialEvent = eventToEdit,
                isDarkMode = isDarkMode,
                onDismiss = { eventToEdit = null },
                onSave = { title, category, timestamp, isRepeating, repeatStartDate, repeatEndDate, repeatDayOfWeek, repeatHour, repeatMinute, repeatAmPm ->
                    val updated = eventToEdit!!.copy(
                        title = title,
                        category = category,
                        targetTimestamp = timestamp,
                        isRepeating = isRepeating,
                        repeatStartDate = repeatStartDate,
                        repeatEndDate = repeatEndDate,
                        repeatDayOfWeek = repeatDayOfWeek,
                        repeatHour = repeatHour,
                        repeatMinute = repeatMinute,
                        repeatAmPm = repeatAmPm
                    )
                    viewModel.updateEvent(updated)
                    eventToEdit = null
                    Toast.makeText(context, "Event Updated!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Full Screen Celeb Confetti Overlay!
        ConfettiOverlay(confettiState = confettiState)
    }
}

@Composable
fun GalaxyBlobBackground(isDarkMode: Boolean) {
    val blobColor1 = if (isDarkMode) Color(0xFF4F46E5).copy(alpha = 0.22f) else Color(0xFF38BDF8).copy(alpha = 0.50f) // Sky Blue
    val blobColor2 = if (isDarkMode) Color(0xFFC084FC).copy(alpha = 0.15f) else Color(0xFFEC4899).copy(alpha = 0.38f) // Vivid Pink
    val blobColor3 = if (isDarkMode) Color(0xFF06B6D4).copy(alpha = 0.10f) else Color(0xFF818CF8).copy(alpha = 0.45f) // Electric Indigo / Lavender

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Top-Left corner glow (Sky Blue)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor1, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(-size.width * 0.15f, -size.height * 0.15f),
                        radius = size.minDimension * 1.1f
                    ),
                    radius = size.minDimension * 1.1f,
                    center = androidx.compose.ui.geometry.Offset(-size.width * 0.15f, -size.height * 0.15f)
                )

                // Top-Right corner glow (Lavender / Indigo glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor3, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 1.15f, -size.height * 0.05f),
                        radius = size.minDimension * 1.0f
                    ),
                    radius = size.minDimension * 1.0f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 1.15f, -size.height * 0.05f)
                )

                // Bottom-Right corner glow (Pink)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor2, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 1.2f, size.height * 1.15f),
                        radius = size.minDimension * 1.2f
                    ),
                    radius = size.minDimension * 1.2f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 1.2f, size.height * 1.15f)
                )

                // Bottom-Left corner glow (Soft Blue-Green / Cyan glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDarkMode) Color(0x12312E81) else Color(0x3560A5FA),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(-size.width * 0.2f, size.height * 1.1f),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = androidx.compose.ui.geometry.Offset(-size.width * 0.2f, size.height * 1.1f)
                )
            }
    )
}

@Composable
fun CountdownHomeView(
    activePinnedEvent: CountdownEvent?,
    allEvents: List<CountdownEvent>,
    currentTime: Long,
    isDarkMode: Boolean,
    onAddClick: () -> Unit,
    onPinClick: (Int) -> Unit,
    onEditClick: (CountdownEvent) -> Unit,
    onDeleteClick: (CountdownEvent) -> Unit,
    onToggleTheme: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF475569)
    val glassBg = if (isDarkMode) Color(0x0EFFFFFF) else Color(0x60FFFFFF)
    val glassBorder = if (isDarkMode) Color(0x1BFFFFFF) else Color(0x66FFFFFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        // App top accent block styled exactly like design
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                // Season Indicator Chip (Month and Autumn/Winter/Spring/Summer)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    val cal = remember { Calendar.getInstance() }
                    val currentMonthVal = cal.get(Calendar.MONTH)
                    val currentSeasonStr = getSeasonForMonth(currentMonthVal).title
                    val currentMonthStr = getMonthName(currentMonthVal)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDarkMode) Color(0x1EFFFFFF) else Color(0x3538BDF8))
                            .border(0.5.dp, if (isDarkMode) Color(0x15FFFFFF) else Color(0x9038BDF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$currentMonthStr · $currentSeasonStr",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF0369A1),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
                Text(
                    text = "Next Event",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Theme Toggle (glassy button with sleek, clean uncolored icon)
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(glassBg)
                        .border(1.dp, glassBorder, RoundedCornerShape(16.dp))
                        .testTag("theme_toggle")
                ) {
                    Icon(
                        painter = painterResource(id = if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon),
                        contentDescription = "Toggle Theme",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Plus Add Button
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(glassBg)
                        .border(1.dp, glassBorder, RoundedCornerShape(16.dp))
                        .testTag("add_event_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Event",
                        tint = textPrimary
                    )
                }
            }
        }

        // Active Pinned Event countdown view
        if (activePinnedEvent != null) {
            val remainingDiff = activePinnedEvent.targetTimestamp - currentTime
            val hasFinished = remainingDiff <= 0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Event category & title
                Text(
                    text = activePinnedEvent.category.uppercase(),
                    color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFFD946EF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Text(
                    text = activePinnedEvent.title,
                    color = textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 3 columns countdown boxes
                if (!hasFinished) {
                    val days = remainingDiff / (24L * 60 * 60 * 1000)
                    val hours = (remainingDiff / (60L * 60 * 1000)) % 24
                    val minutes = (remainingDiff / 60000L) % 60
                    val seconds = (remainingDiff / 1000L) % 60

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        CountdownTimePod(
                            value = String.format("%02d", days),
                            label = "Days",
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                        CountdownTimePod(
                            value = String.format("%02d", hours),
                            label = "Hours",
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                        CountdownTimePod(
                            value = String.format("%02d", minutes),
                            label = "Mins",
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // A subtle dynamic glowing pulsing seconds bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(glassBg)
                            .border(1.dp, glassBorder, RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ACTIVE SYNC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isDarkMode) Color(0xFF34D399) else Color(0xFF10B981))
                            )
                            Text(
                                text = "${seconds}s left",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5)
                            )
                        }
                    }
                } else {
                    // Completed block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(glassBg)
                            .border(1.dp, glassBorder, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎉 EVENT LANDED",
                                color = if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Target date has been successfully reached!",
                                color = textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Empty placeholder
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No active countdowns",
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkMode) Color(0xFF4F46E5) else Color(0xFF6366F1),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("empty_state_add_button")
                ) {
                    Text("Add Your First")
                }
            }
        }

        // Section label
        Text(
            text = "TIMELINE EVENTS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = textSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Events list items
        val otherEvents = allEvents.filter { it.id != activePinnedEvent?.id }

        if (otherEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No other secondary events.\nTap + above to grow your timeline.",
                    color = textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(otherEvents) { event ->
                    EventRowCard(
                        event = event,
                        currentTime = currentTime,
                        isDarkMode = isDarkMode,
                        onPinClick = { onPinClick(event.id) },
                        onEditClick = { onEditClick(event) },
                        onDeleteClick = { onDeleteClick(event) }
                    )
                }
            }
        }
    }
}

@Composable
fun CountdownTimePod(
    value: String,
    label: String,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val glassBg = if (isDarkMode) Color(0x14FFFFFF) else Color(0x50FFFFFF)
    val glassBorder = if (isDarkMode) Color(0x2EFFFFFF) else Color(0x76FFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textLabel = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(24.dp))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary,
                fontFamily = FontFamily.Monospace,
                lineHeight = 36.sp
            )
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textLabel,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
fun EventRowCard(
    event: CountdownEvent,
    currentTime: Long,
    isDarkMode: Boolean,
    onPinClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color(0xFF64748B)
    val glassBg = if (isDarkMode) Color(0x14FFFFFF) else Color(0x50FFFFFF)
    val glassBorder = if (isDarkMode) Color(0x24FFFFFF) else Color(0x76FFFFFF)

    val diff = event.targetTimestamp - currentTime
    val hasEnded = diff <= 0

    val remainingText = if (hasEnded) {
        "Finished"
    } else {
        val d = diff / (24L * 60 * 60 * 1000)
        val h = (diff / (60L * 60 * 1000)) % 24
        if (d > 0) "${d}d ${h}h" else "${h}h left"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("event_item_${event.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (hasEnded) Color.Gray
                    else if (isDarkMode) Color(0xFF34D399) else Color(0xFF10B981)
                )
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = event.category.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                letterSpacing = 0.8.sp
            )
            Text(
                text = event.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Countdown timing stat
        Text(
            text = remainingText,
            color = if (hasEnded) textSecondary else if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5),
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Action menu buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pin/Focus Button using Favourite Core icon (star)
            IconButton(
                onClick = onPinClick,
                modifier = Modifier.size(32.dp).testTag("pin_event_${event.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Pin active display",
                    tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Edit Button
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(32.dp).testTag("edit_event_${event.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit event",
                    tint = textPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Delete Button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp).testTag("delete_event_${event.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete event",
                    tint = Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CalendarEventsView(
    allEvents: List<CountdownEvent>,
    currentTime: Long,
    isDarkMode: Boolean,
    onAddClick: () -> Unit,
    onPinClick: (Int) -> Unit,
    onEditClick: (CountdownEvent) -> Unit,
    onDeleteClick: (CountdownEvent) -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)

    // Sort events by chronological target timestamp
    val sortedEvents = allEvents.sortedBy { it.targetTimestamp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TIME SEQUENCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Text(
                    text = "Timeline Calendar",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )
            }

            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDarkMode) Color(0x0EFFFFFF) else Color(0x15000000))
                    .border(
                        1.dp,
                        if (isDarkMode) Color(0x1BFFFFFF) else Color(0x18000000),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Event",
                    tint = textPrimary
                )
            }
        }

        if (sortedEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events configured yet.\nTap '+' above to start scheduling.",
                    textAlign = TextAlign.Center,
                    color = textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grouping by Month/Year is incredibly clean!
                val sdfGroup = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

                val grouped = sortedEvents.groupBy {
                    sdfGroup.format(Date(it.targetTimestamp))
                }

                grouped.forEach { (monthLabel, eventsInMonth) ->
                    item {
                        Text(
                            text = monthLabel.uppercase(),
                            color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF4F46E5),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(eventsInMonth) { event ->
                        val dateFormatted = SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(Date(event.targetTimestamp))

                        val glassBg = if (isDarkMode) Color(0x12FFFFFF) else Color(0x50FFFFFF)
                        val glassBorder = if (isDarkMode) Color(0x20FFFFFF) else Color(0x76FFFFFF)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(glassBg)
                                .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.category.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = event.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = dateFormatted,
                                    fontSize = 11.sp,
                                    color = textSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onPinClick(event.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Pin Display",
                                            tint = if (event.isPinned) Color(0xFFFBBF24) else textSecondary.copy(alpha = 0.4f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onEditClick(event) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Event",
                                            tint = textPrimary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteClick(event) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Event",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(15.dp)
                                        )
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
fun SettingsAndInfoView(
    allEventsCount: Int,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onTriggerWidgetSync: () -> Unit
) {
    val context = LocalContext.current

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF475569)
    val glassBg = if (isDarkMode) Color(0x10FFFFFF) else Color(0x56FFFFFF)
    val glassBorder = if (isDarkMode) Color(0x22FFFFFF) else Color(0x66FFFFFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "CONFIGURATIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
            Text(
                text = "App Preferences",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            )
        }

        // Dashboard Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("TOTAL TIMELINES", fontSize = 10.sp, color = textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("$allEventsCount", fontSize = 22.sp, color = textPrimary, fontWeight = FontWeight.Black)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("CURRENT RIFT", fontSize = 10.sp, color = textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (isDarkMode) "COSMOS" else "SLATE", fontSize = 22.sp, color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF4F46E5), fontWeight = FontWeight.Black)
                }
            }
        }

        // Settings items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(glassBg)
                .border(1.dp, glassBorder, RoundedCornerShape(28.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Option 1: Toggle Dark Theme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggleTheme() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon),
                        contentDescription = "Theme Icon",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("App Theme", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 14.sp)
                        Text("Active: ${if (isDarkMode) "Cosmic Dark" else "Glassy Light"}", fontSize = 11.sp, color = textSecondary)
                    }
                }
                
                // Sleek, pill visual indicator that matches any display nicely without switches
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkMode) Color(0xFF4F46E5).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f))
                        .border(1.dp, if (isDarkMode) Color(0xFF818CF8) else Color(0xFF38BDF8), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isDarkMode) "COSMIC" else "LIGHT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF0369A1),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Divider(color = glassBorder.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

            // Option 2: Place / Pin Widget on Home Screen (Clean automatic pinning option)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            val myProvider = ComponentName(context, CountdownWidgetProvider::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                appWidgetManager.requestPinAppWidget(myProvider, null, null)
                            } else {
                                Toast.makeText(context, "Pins not supported by launcher", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Requires Android 8.0 or higher", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Pin Widget Icon",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Add Widget to Home Screen", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 14.sp)
                        Text("Place Glass card on your desk automatically", fontSize = 11.sp, color = textSecondary)
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Divider(color = glassBorder.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

            // Option 3: Sync Widgets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTriggerWidgetSync() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh widgets",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Force Widget Sync", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 14.sp)
                        Text("Push current timeline values to home screen", fontSize = 11.sp, color = textSecondary)
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Divider(color = glassBorder.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

            // Option 4: Version Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "App info",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Premium Countdown Glass", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 14.sp)
                        Text("Sleek futuristic glassmorphism edition", fontSize = 11.sp, color = textSecondary)
                    }
                }
                Text(
                    text = "v1.1",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CustomBottomNavBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val glassBg = if (isDarkMode) Color(0x18FFFFFF) else Color(0x6AFFFFFF)
    val glassBorder = if (isDarkMode) Color(0x35FFFFFF) else Color(0x8CFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val activePillBg = if (isDarkMode) Color(0x2AFFFFFF) else Color(0x1E38BDF8)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Tab 0: Countdown Home (Home icon)
        val isTab0Active = activeTab == 0
        IconButton(
            onClick = { onTabSelected(0) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isTab0Active) activePillBg else Color.Transparent)
                .testTag("nav_tab_countdown")
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Active Timelines",
                tint = if (isTab0Active) textPrimary else textPrimary.copy(alpha = 0.4f)
            )
        }

        // Tab 1: Calendar View (List icon)
        val isTab1Active = activeTab == 1
        IconButton(
            onClick = { onTabSelected(1) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isTab1Active) activePillBg else Color.Transparent)
                .testTag("nav_tab_calendar")
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "Timeline Calendar",
                tint = if (isTab1Active) textPrimary else textPrimary.copy(alpha = 0.4f)
            )
        }

        // Tab 2: Settings View (Settings icon)
        val isTab2Active = activeTab == 2
        IconButton(
            onClick = { onTabSelected(2) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isTab2Active) activePillBg else Color.Transparent)
                .testTag("nav_tab_settings")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Preferences",
                tint = if (isTab2Active) textPrimary else textPrimary.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun GlassyEventDialog(
    isEditMode: Boolean,
    initialEvent: CountdownEvent? = null,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: String,
        targetTimestamp: Long,
        isRepeating: Boolean,
        repeatStartDate: Long,
        repeatEndDate: Long,
        repeatDayOfWeek: Int,
        repeatHour: Int,
        repeatMinute: Int,
        repeatAmPm: String
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialEvent?.title ?: "") }
    var category by remember { mutableStateOf(initialEvent?.category ?: "") }
    var targetTimestamp by remember { mutableStateOf(initialEvent?.targetTimestamp ?: System.currentTimeMillis()) }

    var isRepeating by remember { mutableStateOf(initialEvent?.isRepeating ?: false) }
    var repeatStartDate by remember { mutableStateOf(initialEvent?.repeatStartDate ?: System.currentTimeMillis()) }
    var repeatEndDate by remember { mutableStateOf(initialEvent?.repeatEndDate ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) }
    var repeatDayOfWeek by remember { mutableStateOf(initialEvent?.repeatDayOfWeek ?: Calendar.SATURDAY) }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
    val glassBg = if (isDarkMode) Color(0xE60D0D12) else Color(0xEDF1F9FF)
    val glassBorder = if (isDarkMode) Color(0x3CFFFFFF) else Color(0x8CFFFFFF)

    val dateFormatted = SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
        .format(Date(targetTimestamp))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(32.dp))
                .background(glassBg)
                .border(1.5.dp, glassBorder, RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEditMode) "REVISE TARGET" else "SCHEDULE TARGET",
                    color = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF4F46E5),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )

                Text(
                    text = if (isEditMode) "Modify Event" else "Add Timeline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Input field 1: Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        unfocusedLabelColor = textSecondary,
                        focusedLabelColor = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF4F46E5)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_title_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // Input field 2: Category
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category / Subtitle") },
                    placeholder = { Text("e.g. Travel, Personal, Cosmos") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        unfocusedLabelColor = textSecondary,
                        focusedLabelColor = if (isDarkMode) Color(0xFFC084FC) else Color(0xFF4F46E5)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_category_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // Check and show repeat setup if Category contains "class"
                val isClassCategory = category.trim().contains("class", ignoreCase = true)
                androidx.compose.animation.AnimatedVisibility(visible = isClassCategory) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDarkMode) Color(0x11FFFFFF) else Color(0x0A000000))
                            .border(1.dp, glassBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRepeating = !isRepeating }
                        ) {
                            Checkbox(
                                checked = isRepeating,
                                onCheckedChange = { isRepeating = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Is Repeating Class?", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        if (isRepeating) {
                            Text("REPEATING ON DAY OF WEEK", color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val days = listOf("S", "M", "T", "W", "T", "F", "S")
                                val calendarDays = listOf(
                                    Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                                    Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                                )
                                days.forEachIndexed { index, name ->
                                    val calDay = calendarDays[index]
                                    val isSelected = repeatDayOfWeek == calDay
                                    val circleBg = if (isSelected) {
                                        if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5)
                                    } else {
                                        if (isDarkMode) Color(0x22FFFFFF) else Color(0x11000000)
                                    }
                                    val textColor = if (isSelected) Color.White else textPrimary
                                    
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(circleBg)
                                            .clickable { repeatDayOfWeek = calDay }
                                    ) {
                                        Text(name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Start Date picker Button
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDarkMode) Color(0x15FFFFFF) else Color(0x06000000))
                                        .border(1.dp, glassBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            val cal = Calendar.getInstance().apply { timeInMillis = repeatStartDate }
                                            android.app.DatePickerDialog(
                                                context,
                                                { _, yr, mo, dy ->
                                                    val sel = Calendar.getInstance()
                                                    sel.set(Calendar.YEAR, yr)
                                                    sel.set(Calendar.MONTH, mo)
                                                    sel.set(Calendar.DAY_OF_MONTH, dy)
                                                    sel.set(Calendar.HOUR_OF_DAY, 0)
                                                    sel.set(Calendar.MINUTE, 0)
                                                    repeatStartDate = sel.timeInMillis
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text("FROM START DATE", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(repeatStartDate)),
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // End Date picker Button
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDarkMode) Color(0x15FFFFFF) else Color(0x06000000))
                                        .border(1.dp, glassBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            val cal = Calendar.getInstance().apply { timeInMillis = repeatEndDate }
                                            android.app.DatePickerDialog(
                                                context,
                                                { _, yr, mo, dy ->
                                                    val sel = Calendar.getInstance()
                                                    sel.set(Calendar.YEAR, yr)
                                                    sel.set(Calendar.MONTH, mo)
                                                    sel.set(Calendar.DAY_OF_MONTH, dy)
                                                    sel.set(Calendar.HOUR_OF_DAY, 23)
                                                    sel.set(Calendar.MINUTE, 59)
                                                    repeatEndDate = sel.timeInMillis
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text("TO END DATE", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(repeatEndDate)),
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Date Time Picker selector area (with glassy block clickable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkMode) Color(0x15FFFFFF) else Color(0x1B000000))
                        .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            val currentCalendar = Calendar.getInstance()
                            currentCalendar.timeInMillis = targetTimestamp

                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCalendar = Calendar.getInstance()
                                    selectedCalendar.set(Calendar.YEAR, year)
                                    selectedCalendar.set(Calendar.MONTH, month)
                                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                    android.app.TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            selectedCalendar.set(Calendar.MINUTE, minute)
                                            selectedCalendar.set(Calendar.SECOND, 0)
                                            selectedCalendar.set(Calendar.MILLISECOND, 0)
                                            targetTimestamp = selectedCalendar.timeInMillis
                                        },
                                        currentCalendar.get(Calendar.HOUR_OF_DAY),
                                        currentCalendar.get(Calendar.MINUTE),
                                        false // 12-hour clock with AM/PM selection!
                                    ).show()
                                },
                                currentCalendar.get(Calendar.YEAR),
                                currentCalendar.get(Calendar.MONTH),
                                currentCalendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(16.dp)
                        .testTag("dialog_date_picker_trigger"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(if (isClassCategory && isRepeating) "CLASS REMINDER TIME" else "TARGET DATE & TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                        Text(dateFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    }
                    Text(
                        text = "📅",
                        fontSize = 18.sp
                    )
                }

                // Action controls buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, glassBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel")
                    }

                    // Save
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Please write an event title!", Toast.LENGTH_SHORT).show()
                            } else {
                                val catVal = if (category.isBlank()) "General" else category
                                val calTarget = Calendar.getInstance().apply { timeInMillis = targetTimestamp }
                                onSave(
                                    title,
                                    catVal,
                                    targetTimestamp,
                                    isClassCategory && isRepeating,
                                    repeatStartDate,
                                    repeatEndDate,
                                    repeatDayOfWeek,
                                    calTarget.get(Calendar.HOUR_OF_DAY),
                                    calTarget.get(Calendar.MINUTE),
                                    if (calTarget.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkMode) Color(0xFF4F46E5) else Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("dialog_save_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// --- Confetti celebration animations ---

data class ConfettiParticle(
    val id: Int,
    val x: Float,
    val y: Float,
    val speedY: Float,
    val speedX: Float,
    val size: Float,
    val color: Color,
    val rotation: Float,
    val rotationSpeed: Float,
    val opacity: Float = 1.0f,
    val gravity: Float = 0.00035f // gravity pull pull
)

fun triggerConfettiBurst(confettiState: MutableList<ConfettiParticle>) {
    val randomColors = listOf(
        Color(0xFFFF4EAD), // Vivid Pink
        Color(0xFF818CF8), // Violet Blue
        Color(0xFF34D399), // Emerald Green
        Color(0xFFFBBF24), // Gold
        Color(0xFF60A5FA), // Sky Blue
        Color(0xFFC084FC), // Bright Purple
        Color(0xFFF87171)  // Coral Red
    )
    val particles = (1..110).map { id ->
        ConfettiParticle(
            id = id,
            x = (15..85).random() / 100f, // focused bottom areas
            y = 1.05f, // completely at bottom of screen
            speedY = -((18..34).random() / 1000f), // blast upwards fast!
            speedX = ((-12..12).random() / 1000f), // Spread outward
            size = (15..32).random().toFloat(),
            color = randomColors.random(),
            rotation = (0..360).random().toFloat(),
            rotationSpeed = ((-6..6).random()).toFloat().let { if (it == 0f) 3f else it },
            opacity = 1.0f
        )
    }
    confettiState.clear()
    confettiState.addAll(particles)
}

@Composable
fun ConfettiOverlay(confettiState: List<ConfettiParticle>) {
    if (confettiState.isEmpty()) return
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("confetti_canvas")
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        confettiState.forEach { p ->
            val drawX = p.x * canvasWidth
            val drawY = p.y * canvasHeight
            val drawSize = p.size
            
            rotate(p.rotation, pivot = androidx.compose.ui.geometry.Offset(drawX, drawY)) {
                if (p.id % 2 == 0) {
                    drawRect(
                        color = p.color,
                        topLeft = androidx.compose.ui.geometry.Offset(drawX - drawSize / 2, drawY - drawSize / 2),
                        size = androidx.compose.ui.geometry.Size(drawSize, drawSize * 0.6f),
                        alpha = p.opacity
                    )
                } else {
                    drawCircle(
                        color = p.color,
                        center = androidx.compose.ui.geometry.Offset(drawX, drawY),
                        radius = drawSize / 2,
                        alpha = p.opacity
                    )
                }
            }
        }
    }
}


// --- Seasonal Ambient Overlays ---

enum class AppSeason(val title: String) {
    SPRING("Spring ✨"),
    SUMMER("Summer ☀️"),
    AUTUMN("Autumn 🍁"),
    WINTER("Winter ❄️")
}

fun getSeasonForMonth(month: Int): AppSeason {
    return when (month) {
        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> AppSeason.WINTER
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> AppSeason.SPRING
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> AppSeason.SUMMER
        else -> AppSeason.AUTUMN
    }
}

fun getMonthName(month: Int): String {
    return when (month) {
        Calendar.JANUARY -> "January"
        Calendar.FEBRUARY -> "February"
        Calendar.MARCH -> "March"
        Calendar.APRIL -> "April"
        Calendar.MAY -> "May"
        Calendar.JUNE -> "June"
        Calendar.JULY -> "July"
        Calendar.AUGUST -> "August"
        Calendar.SEPTEMBER -> "September"
        Calendar.OCTOBER -> "October"
        Calendar.NOVEMBER -> "November"
        Calendar.DECEMBER -> "December"
        else -> "Month"
    }
}

data class SeasonParticle(
    val id: Int,
    val x: Float,
    val y: Float,
    val speedX: Float,
    val speedY: Float,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val curveAnchor: Float = 0f,
    val currentOpacity: Float = 1.0f
)

@Composable
fun SeasonalAmbientOverlay(isDarkMode: Boolean) {
    val calendar = remember { Calendar.getInstance() }
    val month = calendar.get(Calendar.MONTH)
    val season = remember { getSeasonForMonth(month) }
    
    // Ambient falling particle database
    val particles = remember { mutableStateListOf<SeasonParticle>() }
    
    // Smooth entrance bloom and overlay element fade out state
    val bloomAlpha = remember { androidx.compose.animation.core.Animatable(1.0f) }
    val bloomScale = remember { androidx.compose.animation.core.Animatable(0.7f) }
    
    // Smooth 10s transient full moon visibility timer for nighttime
    val moonAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    
    var triggerAnimCounter by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                triggerAnimCounter++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(season, triggerAnimCounter) {
        val animDuration = if (season == AppSeason.SUMMER) 3500 else 10000
        // Trigger beautiful organic bloom/overlay fade animation
        bloomAlpha.snapTo(1.0f)
        bloomScale.snapTo(0.7f)
        launch {
            bloomAlpha.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = animDuration,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
        launch {
            bloomScale.animateTo(
                targetValue = 1.6f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = animDuration,
                    easing = androidx.compose.animation.core.LinearOutSlowInEasing
                )
            )
        }
        launch {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val isNight = hour >= 18 || hour < 6
            if (isNight) {
                moonAlpha.snapTo(0f)
                // Smoothly fade-in over 1.5 seconds
                moonAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500)
                )
                // Stay fully visible for exactly 7 seconds (making a grand presence)
                delay(7000)
                // Smoothly fade-out over 1.5 seconds (Total duration = 1.5s + 7.0s + 1.5s = 10 seconds!)
                moonAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500)
                )
            } else {
                moonAlpha.snapTo(0f)
            }
        }

        val count = when (season) {
            AppSeason.WINTER -> 35
            AppSeason.SPRING -> 22
            AppSeason.AUTUMN -> 10 // Sparse amount of maples as requested!
            AppSeason.SUMMER -> 0  // No falling particles in Summer
        }
        val randomColors = when (season) {
            AppSeason.WINTER -> listOf(Color(0xE6FFFFFF), Color(0xCCBAE6FD), Color(0x99E2E8F0))
            AppSeason.SPRING -> listOf(Color(0xFFFBCFE8), Color(0xFFFFC0CB), Color(0xFFF472B6)) // Sakura soft pinks
            AppSeason.AUTUMN -> listOf(Color(0xFFE25B38), Color(0xFFD97706), Color(0xFFB45309), Color(0xFFDC2626)) // Maple rich copper/gold/orange
            AppSeason.SUMMER -> listOf()
        }
        
        val list = (1..count).map { id ->
            // Spring petals start exactly on the top-right branch
            val startY = if (season == AppSeason.SPRING) {
                (2..12).random() / 100f
            } else {
                (0..100).random() / 100f // scatter on initialization
            }
            val startX = if (season == AppSeason.SPRING) {
                (55..95).random() / 100f
            } else {
                (0..100).random() / 100f
            }
            
            val customSize = when (season) {
                AppSeason.WINTER -> (6..14).random().toFloat()       // Smaller soft snowflakes
                AppSeason.SPRING -> (12..24).random().toFloat()      // High-resolution blossom petals
                AppSeason.AUTUMN -> (28..40).random().toFloat()      // Beautiful larger recognizable maples
                else -> 10f
            }

            SeasonParticle(
                id = id,
                x = startX,
                y = startY,
                speedY = when (season) {
                    AppSeason.WINTER -> (5..12).random() / 1000f
                    AppSeason.SPRING -> (4..9).random() / 1000f     // Sakura is lighter, descends slower
                    AppSeason.AUTUMN -> (4..10).random() / 1000f      // Moderate leaf weight
                    else -> 5f / 1000f
                },
                speedX = when (season) {
                    AppSeason.WINTER -> ((-4..4).random() / 3000f)
                    AppSeason.SPRING -> ((-16..-6).random() / 3000f) // Sakura drifts left-bound with natural wind
                    AppSeason.AUTUMN -> ((-14..-4).random() / 2500f) // Maple drifts gracefully
                    else -> 0f
                },
                size = customSize,
                rotation = (0..360).random().toFloat(),
                rotationSpeed = when (season) {
                    AppSeason.WINTER -> ((-3..3).random()).toFloat().let { if (it == 0f) 0.5f else it }
                    AppSeason.SPRING -> ((-6..6).random()).toFloat().let { if (it == 0f) 1.5f else it }
                    AppSeason.AUTUMN -> ((-8..8).random()).toFloat().let { if (it == 0f) 2.5f else it }
                    else -> 1.5f
                },
                color = if (randomColors.isNotEmpty()) randomColors.random() else Color.Transparent,
                curveAnchor = (0..100).random() / 10f,
                currentOpacity = 1.0f
            )
        }
        particles.clear()
        particles.addAll(list)
    }
    
    // Physics frames update loop with dynamic natural wind sway and fade effects
    LaunchedEffect(particles.size) {
        if (particles.isNotEmpty()) {
            var time = 0f
            while (true) {
                // If the entrance ambient fade has completely finished, sleep to conserve 100% CPU
                if (bloomAlpha.value <= 0.01f) {
                    delay(200)
                    continue
                }
                delay(20)
                time += 0.03f
                
                val updated = particles.map { p ->
                    var newY = p.y + p.speedY
                    var newX = p.x + p.speedX
                    var newRot = p.rotation + p.rotationSpeed
                    var newOp = p.currentOpacity
                    
                    when (season) {
                        AppSeason.WINTER -> {
                            // Natural snow wind sways
                            val windTimeSway = 0.0022f * kotlin.math.sin(time + p.curveAnchor)
                            newX += windTimeSway
                            // Snow fade out as it approaches the screen bottom
                            if (newY > 0.75f) {
                                newOp = (1.0f - (newY - 0.75f) / 0.25f).coerceIn(0f, 1f)
                            } else {
                                newOp = 1.0f
                            }
                        }
                        AppSeason.SPRING -> {
                            // Sakura caught in natural breeze, drifting diagonally downwards to the left
                            val windBlow = -0.0018f - (0.003f * kotlin.math.sin(time * 0.7f + p.curveAnchor))
                            newX += windBlow
                            newRot += 1.1f * kotlin.math.cos(time * 0.4f + p.id)
                            
                            // Fade out as they fall down past 80% mark
                            if (newY > 0.80f) {
                                newOp = (1.0f - (newY - 0.80f) / 0.20f).coerceIn(0f, 1f)
                            } else {
                                newOp = 1.0f
                            }
                        }
                        AppSeason.AUTUMN -> {
                            // Maple leaf blowing with gusts of natural wind (drifts left)
                            val leafGust = -0.0015f + (0.004f * kotlin.math.sin(time * 1.1f + p.curveAnchor))
                            newX += leafGust
                            newRot += 1.4f * kotlin.math.sin(time + p.id)
                            
                            // Fade out as it drifts down
                            if (newY > 0.78f) {
                                newOp = (1.0f - (newY - 0.78f) / 0.22f).coerceIn(0f, 1f)
                            } else {
                                newOp = 1.0f
                            }
                        }
                        else -> {
                            newOp = 1.0f
                        }
                    }
                    
                    // Recycle particles back to screen top if fully invisible or off screen
                    if (newY > 1.05f || newOp <= 0.01f) {
                        newY = if (season == AppSeason.SPRING) {
                            (2..12).random() / 100f
                        } else {
                            -0.05f
                        }
                        // Spring sakura petals respawn near the cherry blossom branch (top-right side of the screen)
                        newX = if (season == AppSeason.SPRING) {
                            (55..95).random() / 100f
                        } else {
                            (0..100).random() / 100f
                        }
                        newOp = 1.0f
                        newRot = (0..360).random().toFloat()
                    }
                    
                    if (newX > 1.05f) {
                        newX = -0.05f
                        newY = if (season == AppSeason.SPRING) (2..12).random() / 100f else (0..50).random() / 100f
                        newOp = 1.0f
                    } else if (newX < -0.05f) {
                        newX = 1.05f
                        newY = if (season == AppSeason.SPRING) (2..12).random() / 100f else (0..50).random() / 100f
                        newOp = 1.0f
                    }
                    
                    p.copy(
                        y = newY,
                        x = newX,
                        rotation = newRot,
                        currentOpacity = newOp
                    )
                }
                particles.clear()
                particles.addAll(updated)
            }
        }
    }
    
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        val globalAlpha = bloomAlpha.value
        val currentMoonAlpha = moonAlpha.value
        
        // If both the main overlay has fully faded out and the moon has faded out, save painting resources
        if (globalAlpha <= 0.01f && currentMoonAlpha <= 0.01f) return@Canvas
        
        // Spring Sakura Cherry Orchard Branch & Blossom Clusters
        if (season == AppSeason.SPRING) {
            val branchColor = Color(0xFF3E2723).copy(alpha = 0.9f * globalAlpha) // Saddle brown bark
            val twigColor = Color(0xFF4E342E).copy(alpha = 0.85f * globalAlpha) // Lighter brown twigs
            
            // 1. Draw organic tapering main trunk (from top-right corner curving down)
            val trunkPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w, 0f)
                cubicTo(w * 0.92f, h * 0.03f, w * 0.78f, h * 0.06f, w * 0.55f, h * 0.08f)
                lineTo(w * 0.55f, h * 0.09f)
                cubicTo(w * 0.80f, h * 0.07f, w * 0.94f, h * 0.04f, w, h * 0.01f)
                close()
            }
            drawPath(path = trunkPath, color = branchColor)
            
            // 2. Draw organic sub-twigs stretching left and down
            // Twig A
            drawLine(
                color = twigColor,
                start = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.045f),
                end = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.12f),
                strokeWidth = 7f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            // Twig B
            drawLine(
                color = twigColor,
                start = androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.065f),
                end = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.095f),
                strokeWidth = 5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            // Twig C
            drawLine(
                color = twigColor,
                start = androidx.compose.ui.geometry.Offset(w * 0.60f, h * 0.11f),
                end = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.14f),
                strokeWidth = 4.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            // 3. Define the five-petal cherry blossom drawing tool
            val drawCherryBlossom: (androidx.compose.ui.geometry.Offset, Float) -> Unit = { center, r ->
                // Draw five overlapping soft pink round petals
                for (pIndex in 0 until 5) {
                    val angleRad = (pIndex * 72f) * (Math.PI / 180f)
                    val petalX = center.x + (r * 0.52f * kotlin.math.cos(angleRad)).toFloat()
                    val petalY = center.y + (r * 0.52f * kotlin.math.sin(angleRad)).toFloat()
                    
                    drawCircle(
                        color = Color(0xFFFFD1DC).copy(alpha = 0.95f * globalAlpha),
                        center = androidx.compose.ui.geometry.Offset(petalX, petalY),
                        radius = r * 0.55f
                    )
                }
                // Draw warm magenta stamen center
                drawCircle(
                    color = Color(0xFFF472B6).copy(alpha = 0.95f * globalAlpha),
                    center = center,
                    radius = r * 0.35f
                )
                // Draw shiny gold pistil dot
                drawCircle(
                    color = Color(0xFFFFF176).copy(alpha = 0.95f * globalAlpha),
                    center = center,
                    radius = r * 0.15f
                )
            }
            
            // 4. Define twig leafy features
            val drawLeafDetail: (androidx.compose.ui.geometry.Offset, Float) -> Unit = { center, angleDegrees ->
                rotate(angleDegrees, pivot = center) {
                    val leafPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x, center.y)
                        quadraticTo(center.x + 10f, center.y - 5f, center.x + 18f, center.y)
                        quadraticTo(center.x + 10f, center.y + 5f, center.x, center.y)
                    }
                    drawPath(path = leafPath, color = Color(0xCC9CCC65).copy(alpha = globalAlpha))
                }
            }
            
            // 5. Draw fresh green foliage nodes
            drawLeafDetail(androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.055f), -35f)
            drawLeafDetail(androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.085f), 15f)
            drawLeafDetail(androidx.compose.ui.geometry.Offset(w * 0.51f, h * 0.13f), 45f)
            
            // 6. Draw highly-ordered blossom flower clusters directly on twig joints
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.035f), 28f)
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.80f, h * 0.052f), 24f)
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.068f), 22f)
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.12f), 20f)
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.56f, h * 0.082f), 26f)
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.095f), 22f)
            drawCherryBlossom(androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.138f), 19f)
            
            // Sprinkle some tiny buds next to them
            drawCircle(color = Color(0xFFF472B6).copy(alpha = 0.9f * globalAlpha), center = androidx.compose.ui.geometry.Offset(w * 0.61f, h * 0.10f), radius = 6f)
            drawCircle(color = Color(0xFFFFD1DC).copy(alpha = 0.9f * globalAlpha), center = androidx.compose.ui.geometry.Offset(w * 0.59f, h * 0.11f), radius = 8f)
            drawCircle(color = Color(0xFFF472B6).copy(alpha = 0.9f * globalAlpha), center = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.12f), radius = 7f)
        }

        // Render Summer Solar bloom wash and glass refraction halos (which naturally disappear)
        val hourVal = calendar.get(Calendar.HOUR_OF_DAY)
        val isNightTime = hourVal >= 18 || hourVal < 6

        if (season == AppSeason.SUMMER && !isNightTime) {
            val alpha = globalAlpha
            val scale = bloomScale.value
            
            // Blinding solar entrance radial gradient representing sun shining through glass
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFBEB).copy(alpha = 0.90f * alpha),
                        Color(0xFFFDE047).copy(alpha = 0.55f * alpha),
                        Color(0xFFFDBA74).copy(alpha = 0.25f * alpha),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.15f),
                    radius = size.minDimension * 1.5f * scale
                ),
                radius = size.minDimension * 1.5f * scale,
                center = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.15f)
            )

            // Warm double-iris camera lens and glass halo rings that align diagonally
            drawCircle(
                color = Color(0x2838BDF8).copy(alpha = 0.20f * alpha),
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f),
                radius = size.minDimension * 0.24f * scale
            )

            drawCircle(
                color = Color(0x22A78BFA).copy(alpha = 0.22f * alpha),
                center = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.65f),
                radius = size.minDimension * 0.14f * scale
            )

            drawCircle(
                color = Color(0x35F59E0B).copy(alpha = 0.18f * alpha),
                center = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.85f),
                radius = size.minDimension * 0.08f * scale
            )
        }

        // Draw an ultra-realistic moon on top if it is night time across ALL seasons
        if (isNightTime) {
            val moonCenter = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.12f)
            val moonRadius = size.minDimension * 0.08f

            // 1. Moonlight glow / aura (outer glow)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE2E8F0).copy(alpha = 0.5f * currentMoonAlpha),
                        Color(0xFF94A3B8).copy(alpha = 0.2f * currentMoonAlpha),
                        Color.Transparent
                    ),
                    center = moonCenter,
                    radius = moonRadius * 3.5f
                ),
                radius = moonRadius * 3.5f,
                center = moonCenter
            )

            // 2. Main base sphere of the moon with a 3D radial shading (simulates sphere lighting)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFD).copy(alpha = 0.98f * currentMoonAlpha), // bright highlight
                        Color(0xFFF4F3ED).copy(alpha = 0.98f * currentMoonAlpha), // midtone ivory
                        Color(0xFFDCDAD0).copy(alpha = 0.98f * currentMoonAlpha), // shadow gray-ivory
                        Color(0xFFB0AEA3).copy(alpha = 0.98f * currentMoonAlpha)  // dark rim
                    ),
                    center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.15f, moonCenter.y - moonRadius * 0.15f),
                    radius = moonRadius * 1.3f
                ),
                radius = moonRadius,
                center = moonCenter
            )

            // 3. Draw Lunar Maria (the large dark basaltic plains that make the realistic moon face)
            // Maria 1 (Oceanus Procellarum / Mare Imbrium area)
            drawCircle(
                color = Color(0xFF908F85).copy(alpha = 0.35f * currentMoonAlpha),
                center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.35f, moonCenter.y - moonRadius * 0.2f),
                radius = moonRadius * 0.35f
            )
            // Maria 2 (Mare Serenitatis / Tranquillitatis)
            drawCircle(
                color = Color(0xFF908F85).copy(alpha = 0.38f * currentMoonAlpha),
                center = androidx.compose.ui.geometry.Offset(moonCenter.x + moonRadius * 0.25f, moonCenter.y - moonRadius * 0.25f),
                radius = moonRadius * 0.25f
            )
            // Maria 3 (Mare Fecunditatis)
            drawCircle(
                color = Color(0xFF86857A).copy(alpha = 0.35f * currentMoonAlpha),
                center = androidx.compose.ui.geometry.Offset(moonCenter.x + moonRadius * 0.4f, moonCenter.y + moonRadius * 0.15f),
                radius = moonRadius * 0.2f
            )
            // Maria 4 (Mare Nubium / Humorum)
            drawCircle(
                color = Color(0xFF86857A).copy(alpha = 0.35f * currentMoonAlpha),
                center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.25f, moonCenter.y + moonRadius * 0.35f),
                radius = moonRadius * 0.22f
            )

            // 4. Draw detailed craters with shadows and bright illuminated rims to look 3D
            val drawCraterInline: (androidx.compose.ui.geometry.Offset, Float) -> Unit = { craterCenter, craterRadius ->
                // Crater shadow pit
                drawCircle(
                    color = Color(0xFF5A5952).copy(alpha = 0.45f * currentMoonAlpha),
                    center = craterCenter,
                    radius = craterRadius
                )
                // Crater bright rim highlight
                drawCircle(
                    color = Color(0xFFFFFFFF).copy(alpha = 0.75f * currentMoonAlpha),
                    center = androidx.compose.ui.geometry.Offset(craterCenter.x - craterRadius * 0.15f, craterCenter.y - craterRadius * 0.15f),
                    radius = craterRadius * 0.85f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = craterRadius * 0.25f)
                )
            }

            // Southern Highlands & Tycho Crater
            drawCraterInline(androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.1f, moonCenter.y + moonRadius * 0.6f), moonRadius * 0.12f)
            // Draw fine ejecta rays originating from Tycho
            for (i in 0 until 8) {
                val angle = i * (Math.PI / 4.0)
                val rayLength = moonRadius * (0.3f + (i % 3) * 0.15f)
                drawLine(
                    color = Color(0xFFF1F5F9).copy(alpha = 0.28f * currentMoonAlpha),
                    start = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.1f, moonCenter.y + moonRadius * 0.6f),
                    end = androidx.compose.ui.geometry.Offset(
                        (moonCenter.x - moonRadius * 0.1f + rayLength * kotlin.math.cos(angle)).toFloat(),
                        (moonCenter.y + moonRadius * 0.6f + rayLength * kotlin.math.sin(angle)).toFloat()
                    ),
                    strokeWidth = 1.5f
                )
            }

            // Copernicus Crater
            drawCraterInline(androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.45f, moonCenter.y + moonRadius * 0.1f), moonRadius * 0.09f)
            // Kepler Crater
            drawCraterInline(androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.6f, moonCenter.y + moonRadius * 0.22f), moonRadius * 0.07f)
            // Plato Crater (dark floor, bright rim)
            drawCircle(
                color = Color(0xFF6E6D66).copy(alpha = 0.45f * currentMoonAlpha),
                center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.15f, moonCenter.y - moonRadius * 0.65f),
                radius = moonRadius * 0.08f
            )
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = 0.65f * currentMoonAlpha),
                center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.15f, moonCenter.y - moonRadius * 0.65f),
                radius = moonRadius * 0.08f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }
        
        // Draw the active falling season elements adjusted by global alpha
        particles.forEach { p ->
            val drawX = p.x * w
            val drawY = p.y * h
            val dSize = p.size
            val opacity = p.currentOpacity
            
            if (opacity > 0.01f) {
                rotate(p.rotation, pivot = androidx.compose.ui.geometry.Offset(drawX, drawY)) {
                    when (season) {
                        AppSeason.WINTER -> {
                            // Little winter snow crystals
                            drawCircle(
                                color = p.color.copy(alpha = p.color.alpha * opacity * globalAlpha),
                                center = androidx.compose.ui.geometry.Offset(drawX, drawY),
                                radius = dSize / 2f
                            )
                        }
                        AppSeason.SPRING -> {
                            // High-fidelity soft cherry blossom petal oval curve path
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(drawX, drawY - dSize / 2f)
                                cubicTo(
                                    drawX + dSize / 2f, drawY - dSize / 2f,
                                    drawX + dSize * 0.75f, drawY + dSize / 3f,
                                    drawX, drawY + dSize / 2f
                                )
                                cubicTo(
                                    drawX - dSize * 0.75f, drawY + dSize / 3f,
                                    drawX - dSize / 2f, drawY - dSize / 2f,
                                    drawX, drawY - dSize / 2f
                                )
                            }
                            drawPath(path = path, color = p.color.copy(alpha = p.color.alpha * opacity * globalAlpha))
                            
                            // Fine dark pink stamen heart-vein inside the falling petal
                            val innerPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(drawX, drawY - dSize / 4f)
                                cubicTo(
                                    drawX + dSize / 4f, drawY - dSize / 4f,
                                    drawX + dSize * 0.40f, drawY + dSize / 6f,
                                    drawX, drawY + dSize / 4f
                                )
                                cubicTo(
                                    drawX - dSize * 0.40f, drawY + dSize / 6f,
                                    drawX - dSize / 4f, drawY - dSize / 4f,
                                    drawX, drawY - dSize / 4f
                                )
                            }
                            drawPath(path = innerPath, color = Color(0xFFFF8BB0).copy(alpha = 0.9f * opacity * globalAlpha))
                        }
                        AppSeason.AUTUMN -> {
                            // Exquisitely recognizable 5-lobe Canadian organic Maple leaf path
                            val path = androidx.compose.ui.graphics.Path().apply {
                                val rScale = dSize / 2f
                                
                                // Start at stem core connection
                                moveTo(drawX, drawY + rScale * 0.8f)
                                
                                // Leaf stem pointing downwards
                                lineTo(drawX, drawY + rScale * 1.15f)
                                moveTo(drawX, drawY + rScale * 0.8f)
                                
                                // Lower-left lobe
                                lineTo(drawX - rScale * 0.65f, drawY + rScale * 0.45f)
                                lineTo(drawX - rScale * 0.45f, drawY + rScale * 0.25f)
                                
                                // Main-left lobe
                                lineTo(drawX - rScale * 0.98f, drawY - rScale * 0.08f)
                                lineTo(drawX - rScale * 0.55f, drawY - rScale * 0.18f)
                                
                                // Top-center lobe peak
                                lineTo(drawX, drawY - rScale * 1.15f)
                                
                                // Main-right lobe
                                lineTo(drawX + rScale * 0.55f, drawY - rScale * 0.18f)
                                lineTo(drawX + rScale * 0.98f, drawY - rScale * 0.08f)
                                
                                // Lower-right lobe
                                lineTo(drawX + rScale * 0.45f, drawY + rScale * 0.25f)
                                lineTo(drawX + rScale * 0.65f, drawY + rScale * 0.45f)
                                
                                close()
                            }
                            drawPath(path = path, color = p.color.copy(alpha = p.color.alpha * opacity * globalAlpha))
                            
                            // Highly detailed dark-accented leaf veins
                            drawLine(
                                color = Color(0x3B6C200C).copy(alpha = globalAlpha),
                                start = androidx.compose.ui.geometry.Offset(drawX, drawY + dSize * 0.18f),
                                end = androidx.compose.ui.geometry.Offset(drawX, drawY - dSize * 0.45f),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = Color(0x3B6C200C).copy(alpha = globalAlpha),
                                start = androidx.compose.ui.geometry.Offset(drawX, drawY + dSize * 0.18f),
                                end = androidx.compose.ui.geometry.Offset(drawX - dSize * 0.38f, drawY - dSize * 0.12f),
                                strokeWidth = 1.3f
                            )
                            drawLine(
                                color = Color(0x3B6C200C).copy(alpha = globalAlpha),
                                start = androidx.compose.ui.geometry.Offset(drawX, drawY + dSize * 0.18f),
                                end = androidx.compose.ui.geometry.Offset(drawX + dSize * 0.38f, drawY - dSize * 0.12f),
                                strokeWidth = 1.3f
                            )
                        }
                        AppSeason.SUMMER -> {
                            // Summer heat sunlight speck sparklers
                            drawCircle(
                                color = p.color.copy(alpha = p.color.alpha * opacity * globalAlpha),
                                center = androidx.compose.ui.geometry.Offset(drawX, drawY),
                                radius = dSize / 3f,
                                alpha = 0.4f * opacity
                            )
                        }
                    }
                }
            }
        }
    }
}

