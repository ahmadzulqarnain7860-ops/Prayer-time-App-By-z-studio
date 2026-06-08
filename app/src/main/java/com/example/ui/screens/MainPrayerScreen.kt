package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.PrayerViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPrayerScreen(
    viewModel: PrayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val nextPrayerInfo by viewModel.nextPrayerInfo.collectAsState()
    val currentLog by viewModel.currentLog.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val primaryAccentColor = Color(selectedTheme.primaryAccent)
    val cardBgColor = Color(selectedTheme.cardBgColor)

    // Detect screen orientation
    val configuration = LocalConfiguration.current
    val isLandscapeLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // TV / Screen Saver mode overrides
    var forceTvMode by remember { mutableStateOf(false) }
    val isTvMode = isLandscapeLandscape || forceTvMode

    // OLED Screen Defense (subtle drift to prevent physical television screen burn-in)
    var isOledDefenseEnabled by remember { mutableStateOf(true) }
    var driftX by remember { mutableStateOf(0.dp) }
    var driftY by remember { mutableStateOf(0.dp) }

    LaunchedEffect(isOledDefenseEnabled, isTvMode) {
        if (isOledDefenseEnabled && isTvMode) {
            while (true) {
                delay(30000) // gentle drift every 30 seconds
                driftX = ((-15..15).random()).dp
                driftY = ((-10..10).random()).dp
            }
        } else {
            driftX = 0.dp
            driftY = 0.dp
        }
    }

    val animatedDriftX by animateDpAsState(
        targetValue = driftX,
        animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
    )
    val animatedDriftY by animateDpAsState(
        targetValue = driftY,
        animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
    )

    // Dialog state for customizing notification settings
    var showCustomizerDialog by remember { mutableStateOf(false) }
    var dialogPrayerType by remember { mutableStateOf<PrayerType?>(null) }

    // Request permissions for notifications on Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                false 
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Notifications disabled. You won't receive exact prayer alerts.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Mathematical calculations for current day
    val calculator = viewModel.repository.getPrayerTimesForDate(selectedDate)
    val activePrayer = getActivePrayerType(calculator, currentTime)

    val formatterTime = DateTimeFormatter.ofPattern("hh:mm a")
    val formatterDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

    // Compile list of PrayerItems
    val prayerItems = remember(calculator, currentLog) {
        listOf(
            PrayerItem(
                PrayerType.FAJR,
                calculator.fajr,
                viewModel.isNotificationEnabled(PrayerType.FAJR),
                currentLog?.fajrCompleted ?: false
            ),
            PrayerItem(
                PrayerType.SUNRISE,
                calculator.sunrise,
                viewModel.isNotificationEnabled(PrayerType.SUNRISE)
            ),
            PrayerItem(
                PrayerType.DHUHR,
                calculator.dhuhr,
                viewModel.isNotificationEnabled(PrayerType.DHUHR),
                currentLog?.dhuhrCompleted ?: false
            ),
            PrayerItem(
                PrayerType.ASR,
                calculator.asr,
                viewModel.isNotificationEnabled(PrayerType.ASR),
                currentLog?.asrCompleted ?: false
            ),
            PrayerItem(
                PrayerType.MAGHRIB,
                calculator.maghrib,
                viewModel.isNotificationEnabled(PrayerType.MAGHRIB),
                currentLog?.maghribCompleted ?: false
            ),
            PrayerItem(
                PrayerType.ISHA,
                calculator.isha,
                viewModel.isNotificationEnabled(PrayerType.ISHA),
                currentLog?.ishaCompleted ?: false
            )
        )
    }

    // Atmospheric evening gradient dynamically rendered from workspace template
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(selectedTheme.startColor),
            Color(selectedTheme.midColor),
            Color(selectedTheme.endColor)
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            if (isTvMode) {
                // TV SCREENSAVER / AMBIENT CLOCK LAYOUT (WIDESCREEN MODE)
                TvAmbientClockLayout(
                    currentTime = currentTime,
                    selectedDate = selectedDate,
                    formatterDate = formatterDate,
                    nextPrayerInfo = nextPrayerInfo,
                    prayerItems = prayerItems,
                    activePrayer = activePrayer,
                    formatterTime = formatterTime,
                    isOledDefenseEnabled = isOledDefenseEnabled,
                    animatedDriftX = animatedDriftX,
                    animatedDriftY = animatedDriftY,
                    onToggleOledDefense = { isOledDefenseEnabled = !isOledDefenseEnabled },
                    onExitTvMode = { forceTvMode = false },
                    onTriggerTest = { type -> viewModel.triggerImmediateTestNotification(type) },
                    onToggleComplete = { type -> viewModel.togglePrayerCompletion(type) },
                    onOpenSettings = { type ->
                        dialogPrayerType = type
                        showCustomizerDialog = true
                    },
                    selectedTheme = selectedTheme,
                    onOpenThemeSelector = { showThemeDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // PORTRAIT MOBILE LAYOUT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Beautiful asymmetric header with TV Mode trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "MUSCAT, OMAN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccentColor,
                                letterSpacing = 2.5.sp,
                                modifier = Modifier.testTag("location_tag")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTime.format(DateTimeFormatter.ofPattern("hh:mm:ss a")),
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Dynamic Theme Selection Palette Picker
                            IconButton(
                                onClick = { showThemeDialog = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("change_theme_button"),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0x22FFFFFF)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Change Theme",
                                    tint = primaryAccentColor
                                )
                            }

                            // Smart TV Clock mode button
                            IconButton(
                                onClick = { forceTvMode = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("enter_tv_mode_button"),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0x22FFFFFF)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "Enter Ambient TV mode",
                                    tint = primaryAccentColor
                                )
                            }

                            // Hand-crafted Geometric Moon vector decoration
                            GeometricCrescentMoon(
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("moon_decorator"),
                                color = primaryAccentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Glow timer count card with integrated theme parameters
                    nextPrayerInfo?.let { info ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(primaryAccentColor.copy(alpha = 0.25f), Color(0x11FFFFFF))
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .testTag("countdown_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cardBgColor
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "NEXT PRAYER: ${info.name.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xB3FFFFFF),
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = info.countdown,
                                    fontSize = 38.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccentColor,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Dynamic Date Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewModel.setSelectedDate(selectedDate.minusDays(1)) },
                            modifier = Modifier.testTag("prev_day_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Day",
                                tint = Color.White
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = getRelativeDateLabel(selectedDate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccentColor
                            )
                            Text(
                                text = selectedDate.format(formatterDate),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setSelectedDate(selectedDate.plusDays(1)) },
                            modifier = Modifier.testTag("next_day_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Day",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Daily Muscat prayer list (vertical scroll layout)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("prayer_times_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(prayerItems) { item ->
                            val isActive = item.type == activePrayer
                            PrayerItemRow(
                                item = item,
                                isActive = isActive,
                                formatter = formatterTime,
                                primaryAccentColor = primaryAccentColor,
                                onToggleComplete = { viewModel.togglePrayerCompletion(item.type) },
                                onOpenSettings = {
                                    dialogPrayerType = item.type
                                    showCustomizerDialog = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Footer details
                    Text(
                        text = "Times mathematically aligned with Ministry of Endowments, Oman",
                        fontSize = 10.sp,
                        color = Color(0x66FFFFFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

    // Notification Alerts Settings Modal Dialog
    if (showCustomizerDialog && dialogPrayerType != null) {
        val prayerType = dialogPrayerType!!
        val isEnabled = viewModel.isNotificationEnabled(prayerType)
        val selectedTone = viewModel.getNotificationTone(prayerType)

        CustomAlertSettingsDialog(
            prayerType = prayerType,
            isEnabled = isEnabled,
            selectedTone = selectedTone,
            onDismiss = { showCustomizerDialog = false },
            onToggleEnabled = { enabled ->
                viewModel.toggleNotification(prayerType, enabled)
            },
            onSelectTone = { tone ->
                viewModel.setNotificationTone(prayerType, tone)
            },
            onTestAlert = {
                viewModel.triggerImmediateTestNotification(prayerType)
            },
            primaryAccentColor = primaryAccentColor
        )
    }

    // Theme Selection Modal Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = selectedTheme,
            onThemeSelect = { theme ->
                viewModel.setSelectedTheme(theme)
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun TvAmbientClockLayout(
    currentTime: LocalTime,
    selectedDate: LocalDate,
    formatterDate: DateTimeFormatter,
    nextPrayerInfo: PrayerViewModel.NextPrayerInfo?,
    prayerItems: List<PrayerItem>,
    activePrayer: PrayerType,
    formatterTime: DateTimeFormatter,
    isOledDefenseEnabled: Boolean,
    animatedDriftX: androidx.compose.ui.unit.Dp,
    animatedDriftY: androidx.compose.ui.unit.Dp,
    onToggleOledDefense: () -> Unit,
    onExitTvMode: () -> Unit,
    onTriggerTest: (PrayerType) -> Unit,
    onToggleComplete: (PrayerType) -> Unit,
    onOpenSettings: (PrayerType) -> Unit,
    selectedTheme: AppTheme,
    onOpenThemeSelector: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val primaryAccentColor = Color(selectedTheme.primaryAccent)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(if (isLandscape) 24.dp else 16.dp)
    ) {
        // Upper Quick Control Strip (Responsive layout to avoid text wraps/glitches in portrait)
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MUSCAT TV CLOCK",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccentColor,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    AssistChip(
                        onClick = onToggleOledDefense,
                        label = { 
                            Text(
                                text = if (isOledDefenseEnabled) "OLED Defense ACTIVE 🛡️" else "OLED Defense INACTIVE ⚠️",
                                fontSize = 11.sp,
                                color = if (isOledDefenseEnabled) Color(0xFF81C784) else Color(0xFFFF8A80)
                            ) 
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0x1AFFFFFF)
                        ),
                        modifier = Modifier.testTag("tv_oled_defense_chip")
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    AssistChip(
                        onClick = onOpenThemeSelector,
                        label = {
                            Text(
                                text = "Theme: ${selectedTheme.displayName}",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = primaryAccentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0x1DFFFFFF)
                        ),
                        modifier = Modifier.testTag("tv_theme_chip")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Press ⚙️ to adjust sounds",
                        fontSize = 11.sp,
                        color = Color(0x66FFFFFF),
                        modifier = Modifier.padding(end = 16.dp)
                    )

                    Button(
                        onClick = onExitTvMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33FFFFFF),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("exit_tv_mode_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Exit TV Mode", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exit TV Mode", fontSize = 12.sp)
                    }
                }
            }
        } else {
            // PORTRAIT UPPER CONTROL PANEL
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MUSCAT TV CLOCK",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccentColor,
                        letterSpacing = 1.5.sp
                    )

                    Button(
                        onClick = onExitTvMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x22FFFFFF),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("exit_tv_mode_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Exit TV Mode", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exit", fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = onToggleOledDefense,
                            label = { 
                                Text(
                                    text = if (isOledDefenseEnabled) "OLED Defense ACTIVE 🛡️" else "OLED Defense INACTIVE ⚠️",
                                    fontSize = 10.sp,
                                    color = if (isOledDefenseEnabled) Color(0xFF81C784) else Color(0xFFFF8A80)
                                ) 
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0x1AFFFFFF)
                            ),
                            modifier = Modifier.testTag("tv_oled_defense_chip")
                        )

                        IconButton(
                            onClick = onOpenThemeSelector,
                            modifier = Modifier.size(34.dp).testTag("tv_theme_icon_button"),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x15FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Change Theme",
                                tint = primaryAccentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "Press ⚙️ for sounds",
                        fontSize = 11.sp,
                        color = Color(0x66FFFFFF),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .padding(end = 16.dp)
                        .offset(x = animatedDriftX, y = animatedDriftY),
                    contentAlignment = Alignment.Center
                ) {
                    TvLeftClockPanel(
                        currentTime = currentTime,
                        selectedDate = selectedDate,
                        formatterDate = formatterDate,
                        nextPrayerInfo = nextPrayerInfo,
                        isPortrait = false,
                        primaryAccentColor = primaryAccentColor
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvRightPrayerPanel(
                        prayerItems = prayerItems,
                        activePrayer = activePrayer,
                        formatterTime = formatterTime,
                        isPortrait = false,
                        onToggleComplete = onToggleComplete,
                        onOpenSettings = onOpenSettings,
                        primaryAccentColor = primaryAccentColor
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxWidth()
                        .offset(x = animatedDriftX, y = animatedDriftY),
                    contentAlignment = Alignment.Center
                ) {
                    TvLeftClockPanel(
                        currentTime = currentTime,
                        selectedDate = selectedDate,
                        formatterDate = formatterDate,
                        nextPrayerInfo = nextPrayerInfo,
                        isPortrait = true,
                        primaryAccentColor = primaryAccentColor
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvRightPrayerPanel(
                        prayerItems = prayerItems,
                        activePrayer = activePrayer,
                        formatterTime = formatterTime,
                        isPortrait = true,
                        onToggleComplete = onToggleComplete,
                        onOpenSettings = onOpenSettings,
                        primaryAccentColor = primaryAccentColor
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Aligned alignment label
        Text(
            text = "Prayer calculation derived from geographical coordinate mapping (Muscat 23.5859°N, 58.4059°E)",
            fontSize = if (isLandscape) 9.sp else 8.sp,
            color = Color(0x44FFFFFF),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TvLeftClockPanel(
    currentTime: LocalTime,
    selectedDate: LocalDate,
    formatterDate: DateTimeFormatter,
    nextPrayerInfo: PrayerViewModel.NextPrayerInfo?,
    isPortrait: Boolean,
    primaryAccentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "MUSCAT, OMAN",
            fontSize = if (isPortrait) 13.sp else 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = primaryAccentColor,
            letterSpacing = if (isPortrait) 3.sp else 4.sp,
            maxLines = 1
        )
        
        Spacer(modifier = Modifier.height(if (isPortrait) 6.dp else 10.dp))

        // Giant readable clock for smart televisions
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("hh:mm a")),
            fontSize = if (isPortrait) 48.sp else 74.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
            color = Color.White,
            letterSpacing = (-1).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        
        Text(
            text = selectedDate.format(formatterDate).uppercase(),
            fontSize = if (isPortrait) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x99FFFFFF),
            letterSpacing = if (isPortrait) 1.sp else 2.sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(if (isPortrait) 12.dp else 24.dp))

        // Next prayer visual card
        nextPrayerInfo?.let { info ->
            Box(
                modifier = Modifier
                    .border(
                        width = 1.2.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(primaryAccentColor, Color.Transparent),
                            radius = if (isPortrait) 300f else 400f
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(Color(0x13FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = if (isPortrait) 16.dp else 24.dp, vertical = if (isPortrait) 10.dp else 18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "COUNTDOWN TO ${info.name.uppercase()}",
                        fontSize = if (isPortrait) 9.sp else 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryAccentColor,
                        letterSpacing = 2.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(if (isPortrait) 4.dp else 6.dp))
                    Text(
                        text = info.countdown,
                        fontSize = if (isPortrait) 26.sp else 34.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TvRightPrayerPanel(
    prayerItems: List<PrayerItem>,
    activePrayer: PrayerType,
    formatterTime: DateTimeFormatter,
    isPortrait: Boolean,
    onToggleComplete: (PrayerType) -> Unit,
    onOpenSettings: (PrayerType) -> Unit,
    primaryAccentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = if (isPortrait) Arrangement.spacedBy(6.dp) else Arrangement.SpaceEvenly
    ) {
        prayerItems.forEach { item ->
            val isActive = item.type == activePrayer
            val rowBg = if (isActive) primaryAccentColor.copy(alpha = 0.15f) else Color(0x0DFFFFFF)
            val rowBorder = if (isActive) primaryAccentColor else Color.Transparent

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, rowBorder, RoundedCornerShape(14.dp))
                    .background(rowBg, RoundedCornerShape(14.dp))
                    .padding(horizontal = if (isPortrait) 12.dp else 16.dp, vertical = if (isPortrait) 8.dp else 10.dp)
                    .testTag("tv_prayer_row_${item.type.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (isPortrait) 28.dp else 34.dp)
                            .clip(CircleShape)
                            .background(if (isActive) primaryAccentColor else Color(0x15FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getPrayerIcon(item.type),
                            contentDescription = null,
                            tint = if (isActive) Color(0xFF070B18) else Color.White,
                            modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(if (isPortrait) 10.dp else 14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.type.displayName,
                                fontSize = if (isPortrait) 13.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.type.arabicName,
                                fontSize = if (isPortrait) 10.sp else 12.sp,
                                color = Color(0x66FFFFFF)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.time.format(formatterTime),
                        fontSize = if (isPortrait) 13.sp else 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) primaryAccentColor else Color.White
                    )
                    Spacer(modifier = Modifier.width(if (isPortrait) 10.dp else 16.dp))

                    // Action: Habits logger (toggle checkbox)
                    if (item.type.isActualPrayer) {
                        IconButton(
                            onClick = { onToggleComplete(item.type) },
                            modifier = Modifier.size(if (isPortrait) 24.dp else 28.dp).testTag("tv_checkbox_${item.type.id}")
                        ) {
                            Icon(
                                imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                contentDescription = "Logged Completed",
                                tint = if (item.isCompleted) Color(0xFF81C784) else Color(0x40FFFFFF),
                                modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(if (isPortrait) 4.dp else 8.dp))
                    }

                    // Action: Settings gear
                    IconButton(
                        onClick = { onOpenSettings(item.type) },
                        modifier = Modifier.size(if (isPortrait) 24.dp else 28.dp).testTag("tv_settings_${item.type.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isEnabled) Icons.Filled.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Alerts Settings",
                            tint = if (item.isEnabled) primaryAccentColor else Color(0x40FFFFFF),
                            modifier = Modifier.size(if (isPortrait) 16.dp else 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerItemRow(
    item: PrayerItem,
    isActive: Boolean,
    formatter: DateTimeFormatter,
    primaryAccentColor: Color,
    onToggleComplete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val highlightColor = if (isActive) primaryAccentColor.copy(alpha = 0.15f) else Color(0x10FFFFFF)
    val borderColor = if (isActive) primaryAccentColor else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .testTag("prayer_row_${item.type.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = highlightColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left block (Icon and English/Arabic Label)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isActive) primaryAccentColor else Color(0x0DFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getPrayerIcon(item.type),
                        contentDescription = item.type.displayName,
                        tint = if (isActive) Color(0xFF070B18) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.type.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.type.arabicName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0x88FFFFFF)
                        )
                    }
                    Text(
                        text = item.type.description,
                        fontSize = 10.sp,
                        color = Color(0x77FFFFFF)
                    )
                }
            }

            // Right block (Times and interactives)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = item.time.format(formatter),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) primaryAccentColor else Color.White
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Actions panel
                if (item.type.isActualPrayer) {
                    // Completing habit check logger button
                    IconButton(
                        onClick = onToggleComplete,
                        modifier = Modifier.size(24.dp).testTag("checkbox_${item.type.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                            contentDescription = "Logged Completed",
                            tint = if (item.isCompleted) Color(0xFF81C784) else Color(0x40FFFFFF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Configuration modal toggle button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(24.dp).testTag("settings_${item.type.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isEnabled) Icons.Filled.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Alerts Configuration",
                            tint = if (item.isEnabled) primaryAccentColor else Color(0x40FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // For sunrise type, it has no completion check, just notifications
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(24.dp).testTag("settings_${item.type.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isEnabled) Icons.Filled.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Alerts Configuration",
                            tint = if (item.isEnabled) primaryAccentColor else Color(0x40FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomAlertSettingsDialog(
    prayerType: PrayerType,
    isEnabled: Boolean,
    selectedTone: NotificationTone,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectTone: (NotificationTone) -> Unit,
    onTestAlert: () -> Unit,
    primaryAccentColor: Color
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, primaryAccentColor.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF13172E) // Dark slate dialog surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${prayerType.displayName} Alerts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Configure custom notification alerts for Muscat",
                    fontSize = 11.sp,
                    color = Color(0x88FFFFFF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Toggle alarm on/off switch card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x0AFFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Enable Notifications",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF13172E),
                            checkedTrackColor = primaryAccentColor,
                            uncheckedThumbColor = Color(0x66FFFFFF),
                            uncheckedTrackColor = Color(0x15FFFFFF)
                        ),
                        modifier = Modifier.testTag("enable_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tone selection header
                Text(
                    text = "Select Audio Cue Tone",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryAccentColor,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Radio options list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NotificationTone.values().forEach { tone ->
                        val isToneSelected = selectedTone == tone
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isToneSelected) Color(0x0DFFFFFF) else Color.Transparent)
                                .clickable(enabled = isEnabled) { onSelectTone(tone) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isToneSelected,
                                onClick = { onSelectTone(tone) },
                                enabled = isEnabled,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = primaryAccentColor,
                                    unselectedColor = Color(0x33FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = tone.displayName,
                                fontSize = 13.sp,
                                color = if (isEnabled) Color.White else Color(0x33FFFFFF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Close", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onTestAlert,
                        enabled = isEnabled && selectedTone != NotificationTone.SILENT,
                        modifier = Modifier.weight(1.3f).testTag("test_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryAccentColor,
                            contentColor = Color(0xFF13172E)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Alert", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                .testTag("theme_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF111424)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color(currentTheme.primaryAccent),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aesthetic Themes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0x88FFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Personalize backgrounds and atmospheric colors",
                    fontSize = 11.sp,
                    color = Color(0x88FFFFFF),
                    modifier = Modifier.align(Alignment.Start).padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val themes = AppTheme.values()
                    items(themes.size) { index ->
                        val theme = themes[index]
                        val isSelected = currentTheme == theme
                        val itemAccentColor = Color(theme.primaryAccent)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) itemAccentColor else Color(0x11FFFFFF),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(theme.startColor), Color(theme.midColor), Color(theme.endColor))
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onThemeSelect(theme) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = theme.description,
                                    fontSize = 11.sp,
                                    color = Color(0xB3FFFFFF)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF0F1326),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("close_theme_dialog_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(currentTheme.primaryAccent),
                        contentColor = Color(0xFF13172E)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Apply Theme", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GeometricCrescentMoon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFD700)
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val centerPoint = center
        
        // Save graphic canvas context layer
        with(drawContext.canvas.nativeCanvas) {
            val checkpoint = saveLayer(null, null)
            
            // 1. Draw glowing moon base
            drawCircle(
                color = color,
                radius = radius,
                center = centerPoint
            )
            // 2. Clear out sub-surface circle to hollow out the crescent
            drawCircle(
                color = Color.Transparent,
                radius = radius * 0.86f,
                center = Offset(centerPoint.x + radius * 0.44f, centerPoint.y - radius * 0.32f),
                blendMode = BlendMode.Clear
            )
            
            restoreToCount(checkpoint)
        }
    }
}

private fun getPrayerIcon(type: PrayerType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        PrayerType.FAJR -> Icons.Default.WbTwilight
        PrayerType.SUNRISE -> Icons.Default.LightMode
        PrayerType.DHUHR -> Icons.Default.WbSunny
        PrayerType.ASR -> Icons.Default.FilterDrama
        PrayerType.MAGHRIB -> Icons.Default.NightsStay
        PrayerType.ISHA -> Icons.Default.Bedtime
    }
}

private fun getRelativeDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
    }
}

private fun getActivePrayerType(times: PrayerTimesCalculator.PrayerTimes, now: LocalTime): PrayerType {
    val list = listOf(
        Pair(PrayerType.FAJR, times.fajr),
        Pair(PrayerType.SUNRISE, times.sunrise),
        Pair(PrayerType.DHUHR, times.dhuhr),
        Pair(PrayerType.ASR, times.asr),
        Pair(PrayerType.MAGHRIB, times.maghrib),
        Pair(PrayerType.ISHA, times.isha)
    )
    
    // Sort chronological order safety
    var active = PrayerType.ISHA
    if (now.isBefore(times.fajr)) {
        return PrayerType.ISHA // Pre-fajr belongs to tonight's Isha sequence
    }
    for (p in list) {
        if (now.isAfter(p.second) || now == p.second) {
            active = p.first
        }
    }
    return active
}
