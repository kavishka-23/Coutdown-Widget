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
import com.example.CountdownNotificationService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import androidx.compose.ui.res.painterResource
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CountdownApp(viewModel: CountdownViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val pinnedEvent by viewModel.pinnedEvent.collectAsState()

    // Active bottom navigation tab: 0 = Countdown, 1 = Calendar, 2 = Settings
    var activeTab by remember { mutableStateOf(0) }

    // Dialog controllers
    var showAddDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<CountdownEvent?>(null) }

    // Fallback pinned event if none explicitly pinned
    val activePinnedEvent = pinnedEvent ?: allEvents.firstOrNull()

    // Background themes
    val baseBgColor = if (isDarkMode) Color(0xFF050505) else Color(0xFFF1F5F9)

    // Trigger widget updates whenever events or pinned state changes
    LaunchedEffect(allEvents, activePinnedEvent) {
        CountdownWidgetProvider.triggerWidgetUpdate(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        // Aesthetic Gradient Glow Blobs behind the screen
        GalaxyBlobBackground(isDarkMode = isDarkMode)

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
                onSave = { title, category, timestamp ->
                    viewModel.addEvent(title, category, timestamp)
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
                onSave = { title, category, timestamp ->
                    val updated = eventToEdit!!.copy(
                        title = title,
                        category = category,
                        targetTimestamp = timestamp
                    )
                    viewModel.updateEvent(updated)
                    eventToEdit = null
                    Toast.makeText(context, "Event Updated!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun GalaxyBlobBackground(isDarkMode: Boolean) {
    val blobColor1 = if (isDarkMode) Color(0xFF4F46E5).copy(alpha = 0.22f) else Color(0xFF38BDF8).copy(alpha = 0.35f)
    val blobColor2 = if (isDarkMode) Color(0xFFC084FC).copy(alpha = 0.15f) else Color(0xFFF472B6).copy(alpha = 0.30f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Top-Left glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor1, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(-size.width * 0.1f, -size.height * 0.1f),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = androidx.compose.ui.geometry.Offset(-size.width * 0.1f, -size.height * 0.1f)
                )

                // Bottom-Right glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor2, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 1.1f, size.height * 1.1f),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 1.1f, size.height * 1.1f)
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
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
    val glassBg = if (isDarkMode) Color(0x0EFFFFFF) else Color(0x15000000)
    val glassBorder = if (isDarkMode) Color(0x1BFFFFFF) else Color(0x18000000)

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
                Text(
                    text = "MY TIMELINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
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
    val glassBg = if (isDarkMode) Color(0x14FFFFFF) else Color(0x1B000000)
    val glassBorder = if (isDarkMode) Color(0x2EFFFFFF) else Color(0x31000000)
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
    val glassBg = if (isDarkMode) Color(0x14FFFFFF) else Color(0x1B000000)
    val glassBorder = if (isDarkMode) Color(0x24FFFFFF) else Color(0x22000000)

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

                        val glassBg = if (isDarkMode) Color(0x12FFFFFF) else Color(0x1B000000)
                        val glassBorder = if (isDarkMode) Color(0x20FFFFFF) else Color(0x1E000000)

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
    val sharedPrefs = remember { context.getSharedPreferences("premium_countdown_prefs", Context.MODE_PRIVATE) }
    var isNotiEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("noti_bar_countdown", false)) }

    val hasNotiPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sharedPrefs.edit().putBoolean("noti_bar_countdown", true).apply()
            isNotiEnabled = true
            CountdownNotificationService.startService(context)
        } else {
            Toast.makeText(context, "Notification permission is required for live countdown bar", Toast.LENGTH_SHORT).show()
        }
    }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
    val glassBg = if (isDarkMode) Color(0x10FFFFFF) else Color(0x1D000000)
    val glassBorder = if (isDarkMode) Color(0x22FFFFFF) else Color(0x20000000)

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon),
                        contentDescription = "Theme Icon",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Dark Mode Theme", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 14.sp)
                        Text("Toggle cosmic glass vs clinical slate", fontSize = 11.sp, color = textSecondary)
                    }
                }
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF818CF8),
                        checkedTrackColor = Color(0xFF4F46E5)
                    )
                )
            }

            Divider(color = glassBorder.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

            // Option 1.5: Countdown Notification Bar (iOS Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (isNotiEnabled) {
                            sharedPrefs.edit().putBoolean("noti_bar_countdown", false).apply()
                            isNotiEnabled = false
                            CountdownNotificationService.stopService(context)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotiPermission) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                sharedPrefs.edit().putBoolean("noti_bar_countdown", true).apply()
                                isNotiEnabled = true
                                CountdownNotificationService.startService(context)
                            }
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
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notification Icon",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("iOS Notification Bar", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 14.sp)
                        Text("Live system-translucent countdown pill", fontSize = 11.sp, color = textSecondary)
                    }
                }
                Switch(
                    checked = isNotiEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            sharedPrefs.edit().putBoolean("noti_bar_countdown", false).apply()
                            isNotiEnabled = false
                            CountdownNotificationService.stopService(context)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotiPermission) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                sharedPrefs.edit().putBoolean("noti_bar_countdown", true).apply()
                                isNotiEnabled = true
                                CountdownNotificationService.startService(context)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF818CF8),
                        checkedTrackColor = Color(0xFF4F46E5)
                    )
                )
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
    val glassBg = if (isDarkMode) Color(0x18FFFFFF) else Color(0x30000000)
    val glassBorder = if (isDarkMode) Color(0x35FFFFFF) else Color(0x30000000)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val activePillBg = if (isDarkMode) Color(0x2AFFFFFF) else Color(0x2A000000)

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
    onSave: (title: String, category: String, targetTimestamp: Long) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialEvent?.title ?: "") }
    var category by remember { mutableStateOf(initialEvent?.category ?: "") }
    var targetTimestamp by remember { mutableStateOf(initialEvent?.targetTimestamp ?: System.currentTimeMillis()) }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
    val glassBg = if (isDarkMode) Color(0xE60D0D12) else Color(0xE6F8FAFC)
    val glassBorder = if (isDarkMode) Color(0x3CFFFFFF) else Color(0x35000000)

    val dateFormatted = SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
        .format(Date(targetTimestamp))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(32.dp))
                .background(glassBg)
                .border(1.5.dp, glassBorder, RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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

                // Date Time Picker selector area (with glassy block clickable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkMode) Color(0x15FFFFFF) else Color(0x1B000000))
                        .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            // Standard date and time picker flow definition
                            val currentCalendar = Calendar.getInstance()
                            // Set to current custom selected timestamp
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
                                        true
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
                        Text("TARGET DATE & TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
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
                                onSave(title, catVal, targetTimestamp)
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
