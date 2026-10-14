package com.pokernight.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.shared.domain.betting.LegalActions
import com.pokernight.shared.domain.evaluation.HandEvaluator
import com.pokernight.shared.domain.evaluation.HandRank
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.LenderType
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.PotId
import com.pokernight.shared.model.Rank
import com.pokernight.shared.model.SessionPlayerStatus
import com.pokernight.shared.model.Street
import com.pokernight.shared.model.Suit
import kotlinx.coroutines.launch

private val PokerDarkScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    secondary = Color(0xFFFFD54F),
    background = Color(0xFF0F1A12),
    surface = Color(0xFF1E2F23),
    onSurface = Color.White,
    onBackground = Color.White,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokerApp(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Session?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to cancel the entire session? All current hands and chip stacks will be reset.",
                    color = Color.LightGray,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetEntireSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                ) {
                    Text("Confirm Reset", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E2F23),
        )
    }

    MaterialTheme(colorScheme = PokerDarkScheme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = state.currentScreen != CurrentScreen.SETUP,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFF16251A),
                    drawerContentColor = Color.White,
                ) {
                    Text(
                        text = "Poker Night ♠️",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F),
                        modifier = Modifier.padding(18.dp),
                    )
                    HorizontalDivider(color = Color(0xFF2E4532))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF81C784)) },
                        label = { Text("Live Poker Table", fontWeight = FontWeight.SemiBold) },
                        selected = state.currentScreen == CurrentScreen.LIVE_HAND || state.currentScreen == CurrentScreen.INTERMISSION,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (state.liveState != null) {
                                viewModel.navigateTo(CurrentScreen.LIVE_HAND)
                            } else {
                                viewModel.navigateTo(CurrentScreen.INTERMISSION)
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF2E7D32),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.LightGray,
                        ),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFFFD54F)) },
                        label = { Text("Pot Breakdown", fontWeight = FontWeight.SemiBold) },
                        selected = state.currentScreen == CurrentScreen.POT_BREAKDOWN,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateTo(CurrentScreen.POT_BREAKDOWN)
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF2E7D32),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.LightGray,
                        ),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF64B5F6)) },
                        label = { Text("Session Hand History", fontWeight = FontWeight.SemiBold) },
                        selected = state.currentScreen == CurrentScreen.HISTORY,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateTo(CurrentScreen.HISTORY)
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF2E7D32),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.LightGray,
                        ),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFFFFB74D)) },
                        label = { Text("Loans & Bank (0-10%)", fontWeight = FontWeight.SemiBold) },
                        selected = state.currentScreen == CurrentScreen.LOANS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateTo(CurrentScreen.LOANS)
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF2E7D32),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.LightGray,
                        ),
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = Color(0xFF2E4532))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFE57373)) },
                        label = { Text("Reset / Cancel Session", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showResetDialog = true
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                        ),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (state.currentScreen != CurrentScreen.SETUP) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                                }
                            }
                        },
                        title = {
                            val handNum = state.liveState?.hand?.handNumber ?: (state.completedHands.size + 1)
                            Text(
                                text = when (state.currentScreen) {
                                    CurrentScreen.SETUP -> "Poker Night — Setup"
                                    CurrentScreen.LIVE_HAND -> "${state.session?.name ?: "Game"} — Hand #$handNum"
                                    CurrentScreen.POT_BREAKDOWN -> "Pot Breakdown"
                                    CurrentScreen.SHOWDOWN -> "Showdown & Winners"
                                    CurrentScreen.INTERMISSION -> "Between Hands — Hand #$handNum Prep"
                                    CurrentScreen.HISTORY -> "Hand History"
                                    CurrentScreen.LOANS -> "Loans & Bank Ledger"
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF1B3B22),
                            titleContentColor = Color.White,
                        ),
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0F1A12)),
                ) {
                    when (state.currentScreen) {
                        CurrentScreen.SETUP -> SessionSetupScreen(viewModel)
                        CurrentScreen.LIVE_HAND -> LiveHandScreen(viewModel)
                        CurrentScreen.POT_BREAKDOWN -> PotBreakdownScreen(viewModel)
                        CurrentScreen.SHOWDOWN -> ShowdownScreen(viewModel)
                        CurrentScreen.INTERMISSION -> IntermissionScreen(viewModel)
                        CurrentScreen.HISTORY -> HistoryScreen(viewModel)
                        CurrentScreen.LOANS -> LoanScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionSetupScreen(viewModel: PokerViewModel) {
    var sessionName by remember { mutableStateOf("Friday Poker Night") }
    var smallBlindText by remember { mutableStateOf("10") }
    var bigBlindText by remember { mutableStateOf("20") }
    var startingStackText by remember { mutableStateOf("1000") }
    var playerNamesText by remember { mutableStateOf("Alice, Bob, Charlie, Dave") }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF4CAF50),
        unfocusedBorderColor = Color(0xFF81C784),
        focusedLabelColor = Color(0xFFFFD54F),
        unfocusedLabelColor = Color(0xFFB0BEC5),
        focusedContainerColor = Color(0xFF1A2E20),
        unfocusedContainerColor = Color(0xFF1A2E20),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Session Configuration", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))

        OutlinedTextField(
            value = sessionName,
            onValueChange = { sessionName = it },
            label = { Text("Session Name") },
            colors = tfColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = smallBlindText,
                onValueChange = { smallBlindText = it },
                label = { Text("Small Blind (chips)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = tfColors,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = bigBlindText,
                onValueChange = { bigBlindText = it },
                label = { Text("Big Blind (chips)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = tfColors,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = startingStackText,
            onValueChange = { startingStackText = it },
            label = { Text("Starting Stack per Player (chips)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = tfColors,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = playerNamesText,
            onValueChange = { playerNamesText = it },
            label = { Text("Players (comma-separated, clockwise)") },
            colors = tfColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Denomination: 1 chip = ₹0.01 (1 paise)",
            color = Color.LightGray,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val names = playerNamesText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                viewModel.createSession(
                    name = sessionName,
                    smallBlind = smallBlindText.toLongOrNull() ?: 10L,
                    bigBlind = bigBlindText.toLongOrNull() ?: 20L,
                    chipValueInPaise = 1,
                    playerNames = names,
                    startingStack = startingStackText.toLongOrNull() ?: 1000L,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
        ) {
            Text("Start Session", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun LiveHandScreen(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val liveState = state.liveState ?: return
    val legal = state.legalActions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "POT: ${viewModel.formatChipValue(liveState.hand.totalPotChips)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD54F),
                )
                Text(
                    text = "Street: ${liveState.hand.currentStreet} | Live Bet: ${liveState.currentLiveBet}",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (liveState.hand.communityCards.isEmpty()) {
                        Text("No community cards yet", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        liveState.hand.communityCards.forEach { card ->
                            CardView(card)
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(liveState.hand.players) { player ->
                val isTurn = player.playerId == liveState.activePlayerId
                val isDealer = player.playerId == liveState.hand.dealerPlayerId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isTurn) {
                                Modifier.border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            player.folded -> Color(0xFF262626)
                            isTurn -> Color(0xFF2D5A37)
                            else -> Color(0xFF1A2E20)
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = player.playerId.value.substringAfterLast("-"),
                                    fontWeight = FontWeight.Bold,
                                    color = if (player.folded) Color.Gray else Color.White,
                                )
                                if (isDealer) {
                                    Text(
                                        text = " (DEALER)",
                                        color = Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Text(
                                text = "Stack: ${viewModel.formatChipValue(player.remainingStack)}",
                                fontSize = 12.sp,
                                color = Color.LightGray,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Bet: ${player.streetContribution}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFD54F),
                            )
                            if (player.folded) {
                                Text("FOLDED", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else if (player.allIn) {
                                Text("ALL-IN", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (liveState.isRoundComplete || liveState.isHandCompleteByFold) {
            RoundCompleteControls(viewModel)
        } else if (legal != null) {
            ActiveBettingControls(legal, viewModel)
        }
    }
}

@Composable
fun ActiveBettingControls(legal: LegalActions, viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val totalPot = state.liveState?.hand?.totalPotChips ?: 0L

    var betText by remember(legal.minBetAmount) { mutableStateOf(legal.minBetAmount.toString()) }
    var raiseText by remember(legal.minRaiseTo) { mutableStateOf(legal.minRaiseTo.toString()) }

    var sliderBetValue by remember(legal.minBetAmount) { mutableFloatStateOf(legal.minBetAmount.toFloat()) }
    var sliderRaiseValue by remember(legal.minRaiseTo) { mutableFloatStateOf(legal.minRaiseTo.toFloat()) }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFFD54F),
        unfocusedBorderColor = Color(0xFF81C784),
        focusedLabelColor = Color(0xFFFFD54F),
        unfocusedLabelColor = Color.LightGray,
        focusedContainerColor = Color(0xFF142417),
        unfocusedContainerColor = Color(0xFF142417),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (legal.canBet) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Opening Bet:",
                            color = Color(0xFFFFD54F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = betText,
                            onValueChange = { str ->
                                val clean = str.filter { it.isDigit() }
                                betText = clean
                                val num = clean.toLongOrNull() ?: legal.minBetAmount
                                sliderBetValue = num.toFloat().coerceIn(legal.minBetAmount.toFloat(), legal.maxBetAmount.toFloat())
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = tfColors,
                            singleLine = true,
                            modifier = Modifier.size(width = 130.dp, height = 50.dp),
                        )
                    }

                    if (legal.maxBetAmount > legal.minBetAmount) {
                        val betSteps = ((legal.maxBetAmount - legal.minBetAmount) / 5).toInt().coerceAtMost(30)
                        Slider(
                            value = sliderBetValue,
                            onValueChange = {
                                val rounded = (Math.round(it / 5.0) * 5).toLong()
                                sliderBetValue = rounded.toFloat()
                                betText = rounded.toString()
                            },
                            valueRange = legal.minBetAmount.toFloat()..legal.maxBetAmount.toFloat(),
                            steps = betSteps,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                betText = legal.minBetAmount.toString()
                                sliderBetValue = legal.minBetAmount.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        ) {
                            Text("Min", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val current = betText.toLongOrNull() ?: legal.minBetAmount
                                val next = (current + 5L).coerceAtMost(legal.maxBetAmount)
                                betText = next.toString()
                                sliderBetValue = next.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        ) {
                            Text("+5", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val current = betText.toLongOrNull() ?: legal.minBetAmount
                                val next = (current + 10L).coerceAtMost(legal.maxBetAmount)
                                betText = next.toString()
                                sliderBetValue = next.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        ) {
                            Text("+10", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val half = (totalPot / 2).coerceIn(legal.minBetAmount, legal.maxBetAmount)
                                betText = half.toString()
                                sliderBetValue = half.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        ) {
                            Text("1/2 Pot", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (legal.canRaise) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Raise To:",
                            color = Color(0xFFFFD54F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = raiseText,
                            onValueChange = { str ->
                                val clean = str.filter { it.isDigit() }
                                raiseText = clean
                                val num = clean.toLongOrNull() ?: legal.minRaiseTo
                                sliderRaiseValue = num.toFloat().coerceIn(legal.minRaiseTo.toFloat(), legal.maxRaiseTo.toFloat())
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = tfColors,
                            singleLine = true,
                            modifier = Modifier.size(width = 130.dp, height = 50.dp),
                        )
                    }

                    if (legal.maxRaiseTo > legal.minRaiseTo) {
                        val raiseSteps = ((legal.maxRaiseTo - legal.minRaiseTo) / 5).toInt().coerceAtMost(30)
                        Slider(
                            value = sliderRaiseValue,
                            onValueChange = {
                                val rounded = (Math.round(it / 5.0) * 5).toLong()
                                sliderRaiseValue = rounded.toFloat()
                                raiseText = rounded.toString()
                            },
                            valueRange = legal.minRaiseTo.toFloat()..legal.maxRaiseTo.toFloat(),
                            steps = raiseSteps,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                raiseText = legal.minRaiseTo.toString()
                                sliderRaiseValue = legal.minRaiseTo.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        ) {
                            Text("Min", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val current = raiseText.toLongOrNull() ?: legal.minRaiseTo
                                val next = (current + 5L).coerceAtMost(legal.maxRaiseTo)
                                raiseText = next.toString()
                                sliderRaiseValue = next.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        ) {
                            Text("+5", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val current = raiseText.toLongOrNull() ?: legal.minRaiseTo
                                val next = (current + 10L).coerceAtMost(legal.maxRaiseTo)
                                raiseText = next.toString()
                                sliderRaiseValue = next.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        ) {
                            Text("+10", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val pot = totalPot.coerceIn(legal.minRaiseTo, legal.maxRaiseTo)
                                raiseText = pot.toString()
                                sliderRaiseValue = pot.toFloat()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        ) {
                            Text("Pot", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (legal.canFold) {
                Button(
                    onClick = { viewModel.executeAction(ActionType.FOLD) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Fold", fontWeight = FontWeight.Bold)
                }
            }
            if (legal.canCheck) {
                Button(
                    onClick = { viewModel.executeAction(ActionType.CHECK) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Check", fontWeight = FontWeight.Bold)
                }
            }
            if (legal.canCall) {
                Button(
                    onClick = { viewModel.executeAction(ActionType.CALL) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Call ${legal.callAmount}", fontWeight = FontWeight.Bold)
                }
            }
            if (legal.canBet) {
                Button(
                    onClick = {
                        val num = betText.toLongOrNull() ?: legal.minBetAmount
                        viewModel.executeAction(ActionType.BET, num)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Bet", fontWeight = FontWeight.Bold)
                }
            }
            if (legal.canRaise) {
                Button(
                    onClick = {
                        val num = raiseText.toLongOrNull() ?: legal.minRaiseTo
                        viewModel.executeAction(ActionType.RAISE, num)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Raise", fontWeight = FontWeight.Bold)
                }
            }
            if (legal.canAllIn) {
                Button(
                    onClick = { viewModel.executeAction(ActionType.ALL_IN) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("All-In", fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { viewModel.undoAction() }) {
                Text("Undo Action", color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RoundCompleteControls(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val liveState = state.liveState ?: return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (liveState.isHandCompleteByFold) {
            Button(
                onClick = { viewModel.resolveHandWithWinners(emptyMap()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("Award Uncontested Pot to Winner", fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            when (liveState.hand.currentStreet) {
                Street.PRE_FLOP -> {
                    Button(
                        onClick = {
                            viewModel.advanceStreet(
                                listOf(Card.parse("Ah"), Card.parse("Kd"), Card.parse("Qc")),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Deal Flop (3 Cards)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Street.FLOP -> {
                    Button(
                        onClick = { viewModel.advanceStreet(listOf(Card.parse("Jc"))) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Deal Turn (1 Card)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Street.TURN -> {
                    Button(
                        onClick = { viewModel.advanceStreet(listOf(Card.parse("10s"))) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Deal River (1 Card)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Street.RIVER, Street.SHOWDOWN -> {
                    Button(
                        onClick = { viewModel.navigateTo(CurrentScreen.SHOWDOWN) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Go to Showdown & Award Pots", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { viewModel.undoAction() }) {
                Text("Undo Action", color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PotBreakdownScreen(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val potResult = state.potCalculation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pot & Side Pots Breakdown", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))

        if (potResult != null) {
            potResult.pots.forEach { pot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${pot.potType} Pot — ${viewModel.formatChipValue(pot.amount)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                        )
                        Text(
                            text = "Eligible Players: ${pot.eligiblePlayerIds.joinToString { it.value.substringAfterLast("-") }}",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            if (potResult.uncalledExcessRefunds.isNotEmpty()) {
                val refundsText = potResult.uncalledExcessRefunds.entries.joinToString {
                    "${it.key.value.substringAfterLast("-")}: ${it.value} chips"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332A15)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Returned Uncalled Excess:", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                        Text(refundsText, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (state.liveState != null) {
                    viewModel.navigateTo(CurrentScreen.LIVE_HAND)
                } else {
                    viewModel.navigateTo(CurrentScreen.INTERMISSION)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("Back", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun ShowdownScreen(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val potResult = state.potCalculation ?: return
    val selectedWinners = remember { mutableStateMapOf<PotId, Set<PlayerId>>() }
    var showCardPicker by remember { mutableStateOf(false) }

    potResult.pots.forEach { pot ->
        if (!selectedWinners.containsKey(pot.id)) {
            selectedWinners[pot.id] = emptySet()
        }
    }

    val allPotsHaveWinners = potResult.pots.all { (selectedWinners[it.id] ?: emptySet()).isNotEmpty() }

    if (showCardPicker) {
        CardEvaluationDialog(
            viewModel = viewModel,
            onDismiss = { showCardPicker = false },
            onWinnersEvaluated = { evaluatedWinners ->
                potResult.pots.forEach { pot ->
                    selectedWinners[pot.id] = evaluatedWinners
                }
                showCardPicker = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Select Winners", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))

            Button(
                onClick = { showCardPicker = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
            ) {
                Icon(Icons.Default.Style, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Evaluate with Cards", fontSize = 12.sp, color = Color.White)
            }
        }

        if (potResult.uncalledExcessRefunds.isNotEmpty()) {
            val refundInfo = potResult.uncalledExcessRefunds.entries.joinToString {
                "${it.key.value.substringAfterLast("-")}: ${it.value} chips"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF332A15)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Uncalled Excess Returned: $refundInfo",
                    color = Color(0xFFFFD54F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(potResult.pots) { pot ->
                val currentWinners = selectedWinners[pot.id] ?: emptySet()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${pot.potType} Pot (${viewModel.formatChipValue(pot.amount)})",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                        )

                        Text("Tap to mark winner(s):", fontSize = 12.sp, color = Color.LightGray)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pot.eligiblePlayerIds.forEach { pId ->
                                val isSelected = currentWinners.contains(pId)
                                val displayName = pId.value.substringAfterLast("-")

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedWinners[pot.id] = if (isSelected) {
                                                currentWinners - pId
                                            } else {
                                                currentWinners + pId
                                            }
                                        }
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(3.dp, Color(0xFFFFD54F), RoundedCornerShape(10.dp))
                                            } else {
                                                Modifier.border(1.dp, Color(0xFF455A64), RoundedCornerShape(10.dp))
                                            },
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFF132216),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .background(
                                                        color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF263238),
                                                        shape = RoundedCornerShape(6.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isSelected) {
                                                    Text("✓", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                                                )
                                                Text(
                                                    text = if (isSelected) "SELECTED AS WINNER" else "Tap to mark as winner",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else Color.Gray,
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFFFD54F), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text(
                                                    text = "🏆 WINNER",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
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

        Button(
            onClick = {
                viewModel.resolveHandWithWinners(selectedWinners.mapValues { it.value.toList() })
            },
            enabled = allPotsHaveWinners,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                disabledContainerColor = Color(0xFF1B3B22),
            ),
        ) {
            Text(
                text = if (allPotsHaveWinners) "Confirm Payouts & Go to Intermission" else "Select at least 1 winner for each pot",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (allPotsHaveWinners) Color.White else Color.Gray,
            )
        }
    }
}

@Composable
fun CardEvaluationDialog(
    viewModel: PokerViewModel,
    onDismiss: () -> Unit,
    onWinnersEvaluated: (Set<PlayerId>) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val hand = state.liveState?.hand ?: return
    val activePlayers = hand.players.filter { !it.folded }

    val boardCards = remember {
        mutableStateMapOf<Int, Card?>().apply { (0..4).forEach { put(it, hand.communityCards.getOrNull(it)) } }
    }
    val holeCards = remember {
        mutableStateMapOf<PlayerId, Pair<Card?, Card?>>().apply {
            activePlayers.forEach { put(it.playerId, Pair(null, null)) }
        }
    }

    var selectedSlot by remember { mutableStateOf("board_0") }
    var selectedSuit by remember { mutableStateOf(Suit.SPADES) }
    var evaluationSummary by remember { mutableStateOf<Map<PlayerId, HandRank>?>(null) }

    val allPickedCards = mutableSetOf<Card>()
    boardCards.values.filterNotNull().forEach { allPickedCards.add(it) }
    holeCards.values.forEach { (c1, c2) ->
        c1?.let { allPickedCards.add(it) }
        c2?.let { allPickedCards.add(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input Cards for Evaluation", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tap slot, then pick suit & rank:", fontSize = 12.sp, color = Color(0xFFFFD54F))

                // Board Cards (5 Slots labelled B1 to B5)
                Text("Board (5 Cards):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..4).forEach { idx ->
                        val slotKey = "board_$idx"
                        val isSlotActive = selectedSlot == slotKey
                        val card = boardCards[idx]
                        CardSlotView(slotLabel = "B${idx + 1}", card = card, isActive = isSlotActive) {
                            selectedSlot = slotKey
                        }
                    }
                }

                // Players' Hole Cards (labelled C1 and C2)
                Text("Player Hole Cards:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                activePlayers.forEach { p ->
                    val pName = p.playerId.value.substringAfterLast("-")
                    val (c1, c2) = holeCards[p.playerId] ?: Pair(null, null)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(pName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CardSlotView(slotLabel = "C1", card = c1, isActive = selectedSlot == "${p.playerId.value}_0") {
                                selectedSlot = "${p.playerId.value}_0"
                            }
                            CardSlotView(slotLabel = "C2", card = c2, isActive = selectedSlot == "${p.playerId.value}_1") {
                                selectedSlot = "${p.playerId.value}_1"
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF2E4532), modifier = Modifier.padding(vertical = 4.dp))

                // 4 Suits: ICONS ONLY (♠, ♥, ♦, ♣)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Suit.entries.forEach { suit ->
                        val isSelected = selectedSuit == suit
                        val suitColor = if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) Color(0xFFFF5252) else Color.White
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .height(46.dp)
                                .background(
                                    if (isSelected) Color(0xFF2E7D32) else Color(0xFF1E2E22),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    if (isSelected) 2.5.dp else 1.dp,
                                    if (isSelected) Color(0xFFFFD54F) else Color(0xFF455A64),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { selectedSuit = suit },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = suit.symbol.toString(),
                                color = suitColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                            )
                        }
                    }
                }

                // 13 Ranks: ZERO-PADDING TILES WITH LARGE CRISP NUMBERS
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val row1 = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT)
                    val row2 = listOf(Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row1.forEach { rank ->
                            val card = Card(rank, selectedSuit)
                            val isAlreadyUsed = allPickedCards.contains(card)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .background(
                                        color = if (isAlreadyUsed) Color(0xFF141A15) else Color(0xFF1E3A24),
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isAlreadyUsed) Color(0xFF263228) else Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .clickable(enabled = !isAlreadyUsed) {
                                        if (selectedSlot.startsWith("board_")) {
                                            val bIdx = selectedSlot.substringAfter("board_").toInt()
                                            boardCards[bIdx] = card
                                            if (bIdx < 4) selectedSlot = "board_${bIdx + 1}"
                                        } else {
                                            val pIdStr = selectedSlot.substringBeforeLast("_")
                                            val cIdx = selectedSlot.substringAfterLast("_").toInt()
                                            val pId = PlayerId(pIdStr)
                                            val currentPair = holeCards[pId] ?: Pair(null, null)
                                            holeCards[pId] = if (cIdx == 0) Pair(card, currentPair.second) else Pair(currentPair.first, card)
                                            if (cIdx == 0) selectedSlot = "${pIdStr}_1"
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = rank.symbol,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAlreadyUsed) Color(0xFF37474F) else Color.White,
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row2.forEach { rank ->
                            val card = Card(rank, selectedSuit)
                            val isAlreadyUsed = allPickedCards.contains(card)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .background(
                                        color = if (isAlreadyUsed) Color(0xFF141A15) else Color(0xFF1E3A24),
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isAlreadyUsed) Color(0xFF263228) else Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .clickable(enabled = !isAlreadyUsed) {
                                        if (selectedSlot.startsWith("board_")) {
                                            val bIdx = selectedSlot.substringAfter("board_").toInt()
                                            boardCards[bIdx] = card
                                            if (bIdx < 4) selectedSlot = "board_${bIdx + 1}"
                                        } else {
                                            val pIdStr = selectedSlot.substringBeforeLast("_")
                                            val cIdx = selectedSlot.substringAfterLast("_").toInt()
                                            val pId = PlayerId(pIdStr)
                                            val currentPair = holeCards[pId] ?: Pair(null, null)
                                            holeCards[pId] = if (cIdx == 0) Pair(card, currentPair.second) else Pair(currentPair.first, card)
                                            if (cIdx == 0) selectedSlot = "${pIdStr}_1"
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (rank == Rank.TEN) "10" else rank.symbol,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAlreadyUsed) Color(0xFF37474F) else Color.White,
                                )
                            }
                        }
                    }
                }

                evaluationSummary?.let { summary ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF142C1B)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("Evaluation Result:", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F), fontSize = 12.sp)
                            summary.forEach { (pId, rank) ->
                                Text(
                                    text = "${pId.value.substringAfterLast("-")}: ${rank.description}",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalBoard = boardCards.values.filterNotNull()
                    require(finalBoard.size == 5) { "Please input all 5 board cards" }
                    activePlayers.forEach { p ->
                        val pair = holeCards[p.playerId]
                        require(pair?.first != null && pair.second != null) {
                            "Please input 2 cards for ${p.playerId.value.substringAfterLast("-")}"
                        }
                    }

                    val ranks = mutableMapOf<PlayerId, HandRank>()
                    activePlayers.forEach { p ->
                        val (c1, c2) = holeCards[p.playerId]!!
                        val seven = listOf(c1!!, c2!!) + finalBoard
                        ranks[p.playerId] = HandEvaluator.evaluate7Cards(seven)
                    }
                    evaluationSummary = ranks

                    var best: HandRank? = null
                    val winners = mutableSetOf<PlayerId>()
                    ranks.forEach { (pId, rank) ->
                        if (best == null || rank > best!!) {
                            best = rank
                            winners.clear()
                            winners.add(pId)
                        } else if (rank.compareTo(best!!) == 0) {
                            winners.add(pId)
                        }
                    }

                    onWinnersEvaluated(winners)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            ) {
                Text("Evaluate & Auto-Select Winners", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF16251A),
    )
}

@Composable
fun CardSlotView(slotLabel: String, card: Card?, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 40.dp)
            .background(if (card != null) Color.White else Color(0xFF142417), RoundedCornerShape(4.dp))
            .border(if (isActive) 2.5.dp else 1.dp, if (isActive) Color(0xFFFFD54F) else Color.Gray, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (card != null) {
            val suitColor = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) Color.Red else Color.Black
            val rankText = if (card.rank == Rank.TEN) "10" else card.rank.symbol
            Text("$rankText${card.suit.symbol}", color = suitColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            Text(slotLabel, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun IntermissionScreen(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session ?: return
    val nextHandNum = state.completedHands.size + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Intermission", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                Text("Preparing Hand #$nextHandNum", color = Color.LightGray, fontSize = 13.sp)
            }

            Button(
                onClick = { viewModel.navigateTo(CurrentScreen.LOANS) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
            ) {
                Text("Handout Loans", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        state.lastResolutionResult?.let { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2F20)),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Last Hand Payouts:", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F), fontSize = 13.sp)
                    res.payouts.forEach { pay ->
                        val name = pay.playerId.value.substringAfterLast("-")
                        Text(
                            text = "• $name received ${viewModel.formatChipValue(pay.amount)} (${pay.payoutType})",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Text("Player Lineup & Stacks:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(session.players) { sp ->
                val isDealer = sp.playerId == session.dealerPlayerId
                val isSittingOut = sp.status == SessionPlayerStatus.SITTING_OUT

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSittingOut) Color(0xFF242424) else Color(0xFF1E3A24),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sp.playerId.value.substringAfterLast("-"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (isSittingOut) Color.Gray else Color.White,
                                )
                                if (isDealer) {
                                    Text(
                                        text = " (NEXT DEALER)",
                                        color = Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Text(
                                text = "Stack: ${viewModel.formatChipValue(sp.currentStack)}",
                                fontSize = 13.sp,
                                color = if (isSittingOut) Color.Gray else Color.LightGray,
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleSittingOut(sp.playerId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSittingOut) Color(0xFF616161) else Color(0xFF2E7D32),
                            ),
                        ) {
                            Text(
                                text = if (isSittingOut) "Sitting Out" else "Active",
                                fontSize = 12.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.startNewHand(session, session.dealerPlayerId)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
        ) {
            Text(
                text = "Deal Hand #$nextHandNum",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
fun HistoryScreen(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Completed Hands Log", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.completedHands.isEmpty()) {
                item {
                    Text("No hands completed yet in this session.", color = Color.Gray, fontSize = 14.sp)
                }
            }
            items(state.completedHands) { hand ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Hand #${hand.handNumber}", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                        Text(
                            text = "Dealer: ${hand.dealerPlayerId.value.substringAfterLast("-")} | Pot: ${hand.totalPotChips} chips",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                        if (hand.communityCards.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                hand.communityCards.forEach { card ->
                                    CardView(card)
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (state.liveState != null) {
                    viewModel.navigateTo(CurrentScreen.LIVE_HAND)
                } else {
                    viewModel.navigateTo(CurrentScreen.INTERMISSION)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
        ) {
            Text("Back", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun LoanScreen(viewModel: PokerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session ?: return

    var selectedBorrower by remember { mutableStateOf(session.players.first().playerId) }
    var lenderType by remember { mutableStateOf(LenderType.BANK) }
    var selectedLenderPlayer by remember {
        mutableStateOf(session.players.firstOrNull { it.playerId != selectedBorrower }?.playerId)
    }
    var loanAmountText by remember { mutableStateOf("500") }
    var interestRate by remember { mutableFloatStateOf(0f) }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFFD54F),
        unfocusedBorderColor = Color(0xFF81C784),
        focusedLabelColor = Color(0xFFFFD54F),
        unfocusedLabelColor = Color.LightGray,
        focusedContainerColor = Color(0xFF142417),
        unfocusedContainerColor = Color(0xFF142417),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Loan Management", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))

            OutlinedButton(
                onClick = {
                    if (state.liveState != null) {
                        viewModel.navigateTo(CurrentScreen.LIVE_HAND)
                    } else {
                        viewModel.navigateTo(CurrentScreen.INTERMISSION)
                    }
                },
            ) {
                Text("Back", color = Color(0xFFFFD54F))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Issue New Loan", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)

                Text("Borrower:", fontSize = 13.sp, color = Color.LightGray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    session.players.forEach { p ->
                        val isBorrower = p.playerId == selectedBorrower
                        Button(
                            onClick = {
                                selectedBorrower = p.playerId
                                if (selectedLenderPlayer == p.playerId) {
                                    selectedLenderPlayer = session.players.firstOrNull { it.playerId != p.playerId }?.playerId
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBorrower) Color(0xFF2E7D32) else Color(0xFF1A261D),
                            ),
                        ) {
                            Text(p.playerId.value.substringAfterLast("-"), color = Color.White)
                        }
                    }
                }

                Text("Lender:", fontSize = 13.sp, color = Color.LightGray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = lenderType == LenderType.BANK,
                        onClick = { lenderType = LenderType.BANK },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD54F)),
                    )
                    Text("House Bank", color = Color.White, modifier = Modifier.clickable { lenderType = LenderType.BANK })

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = lenderType == LenderType.PLAYER,
                        onClick = { lenderType = LenderType.PLAYER },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD54F)),
                    )
                    Text("Other Player", color = Color.White, modifier = Modifier.clickable { lenderType = LenderType.PLAYER })
                }

                if (lenderType == LenderType.PLAYER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        session.players.filter { it.playerId != selectedBorrower }.forEach { p ->
                            val isLender = p.playerId == selectedLenderPlayer
                            Button(
                                onClick = { selectedLenderPlayer = p.playerId },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLender) Color(0xFFE65100) else Color(0xFF1A261D),
                                ),
                            ) {
                                Text("${p.playerId.value.substringAfterLast("-")} (${p.currentStack})", color = Color.White)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = loanAmountText,
                    onValueChange = { loanAmountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Loan Amount (chips)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                val principal = loanAmountText.toLongOrNull() ?: 0L
                val interestInt = interestRate.toInt()
                val totalRepay = com.pokernight.shared.model.LoanRecord.calculateTotalRepay(principal, interestInt)

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Interest Rate: $interestInt%", color = Color.White, fontSize = 13.sp)
                        Text("Repayment: $totalRepay chips", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = interestRate,
                        onValueChange = { interestRate = it },
                        valueRange = 0f..10f,
                        steps = 9,
                    )
                }

                Button(
                    onClick = {
                        viewModel.issueLoan(
                            borrowerId = selectedBorrower,
                            lenderType = lenderType,
                            lenderPlayerId = if (lenderType == LenderType.PLAYER) selectedLenderPlayer else null,
                            principal = principal,
                            interestPercent = interestInt,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                ) {
                    Text("Grant Loan", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Text("Active Loans:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loans.isEmpty()) {
                item {
                    Text("No loans issued yet", color = Color.Gray, fontSize = 13.sp)
                }
            }
            items(state.loans) { loan ->
                val borrowerName = loan.borrowerId.value.substringAfterLast("-")
                val lenderName = if (loan.lenderType == LenderType.BANK) "Bank" else loan.lenderPlayerId?.value?.substringAfterLast("-") ?: "Player"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (loan.isRepaid) Color(0xFF1B241C) else Color(0xFF263829),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "$borrowerName borrowed ${loan.principalAmount} from $lenderName",
                                fontWeight = FontWeight.Bold,
                                color = if (loan.isRepaid) Color.Gray else Color.White,
                            )
                            Text(
                                text = "Interest: ${loan.interestPercent}% | Owed: ${loan.totalRepayAmount} chips",
                                fontSize = 12.sp,
                                color = if (loan.isRepaid) Color.Gray else Color(0xFFFFD54F),
                            )
                        }

                        if (!loan.isRepaid) {
                            Button(
                                onClick = { viewModel.repayLoan(loan.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            ) {
                                Text("Repay", fontSize = 12.sp)
                            }
                        } else {
                            Text("REPAID", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardView(card: Card) {
    val suitColor = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) Color.Red else Color.Black
    val rankText = if (card.rank == Rank.TEN) "10" else card.rank.symbol
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$rankText${card.suit.symbol}",
            color = suitColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}
