package com.example.kuereader.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.muhakmal.kuereader.KueReader
import io.github.muhakmal.kuereader.NfcManager
import io.github.muhakmal.kuereader.NfcState
import io.github.muhakmal.kuereader.model.CardData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var nfcManager: NfcManager
    private val scannedCard = mutableStateOf<CardData?>(null)
    private val statusMessage = mutableStateOf("Ready to scan. Tap your card to the back of your phone.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcManager = NfcManager(this)

        lifecycleScope.launch {
            nfcManager.nfcTags.collectLatest { tag ->
                statusMessage.value = "Reading card..."
                val result = KueReader.readTag(tag)
                scannedCard.value = result
                statusMessage.value = if (result != null) {
                    "Read success: ${result.cardName}"
                } else {
                    "Card unreadable or unrecognized."
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SampleScreen(
                        nfcState = nfcManager.nfcState.collectAsState().value,
                        status = statusMessage.value,
                        card = scannedCard.value,
                        onOpenSettings = { NfcManager.openNfcSettings(this) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcManager.enableReaderMode(this)
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableReaderMode(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(
    nfcState: NfcState,
    status: String,
    card: CardData?,
    onOpenSettings: () -> Unit
) {
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KUE Reader SDK Sample", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // NFC State indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (nfcState) {
                        NfcState.READY -> Color(0xFFE8F5E9)
                        NfcState.DISABLED -> Color(0xFFFFF3E0)
                        NfcState.UNSUPPORTED -> Color(0xFFFFEBEE)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NFC Status: $nfcState",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    if (nfcState == NfcState.DISABLED) {
                        TextButton(onClick = onOpenSettings) {
                            Text("Turn On")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = status,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (card != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = card.cardName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (card.balanceKnown) {
                                currencyFormatter.format(card.balance)
                            } else {
                                "Saldo Tidak Terbaca"
                            },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (card.rawId.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "UID: ${card.rawId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                if (card.transactionHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recent Transactions (${card.transactionHistory.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(card.transactionHistory) { tx ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(tx.type, fontWeight = FontWeight.SemiBold)
                                        Text(tx.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        if (tx.location != null) {
                                            Text("Loc: ${tx.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    Text(
                                        currencyFormatter.format(tx.amount),
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.type.contains("Top", ignoreCase = true)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
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
