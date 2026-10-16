package com.baskom.pembacakue.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.BillingManager
import com.baskom.pembacakue.ui.theme.BgSurface
import com.baskom.pembacakue.ui.theme.BgBase
import com.baskom.pembacakue.ui.theme.Violet
import com.baskom.pembacakue.ui.theme.TextMuted
import com.baskom.pembacakue.ui.theme.TextPrimary
import com.baskom.pembacakue.ui.theme.TextSecondary

import com.baskom.pembacakue.findActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    billingManager: BillingManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val products by billingManager.productDetails.collectAsState(initial = emptyList())
    val isAdFree by billingManager.isAdFree.collectAsState(initial = false)
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = { Text("Premium", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgBase,
                    scrolledContainerColor = BgBase
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Violet.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = "Premium",
                    tint = Violet,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = if (isAdFree) "Anda Sudah Premium!" else "Bebas Iklan",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (isAdFree) 
                    "Terima kasih telah mendukung kami. Aplikasi Anda sekarang bebas dari semua iklan selamanya atau selama masa aktif langganan Anda." 
                else 
                    "Dukung developer dan nikmati aplikasi tanpa gangguan iklan banner maupun iklan layar penuh.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            if (!isAdFree) {
                if (products.isEmpty()) {
                    CircularProgressIndicator(color = Violet)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Memuat paket langganan...", color = TextMuted, fontSize = 12.sp)
                } else {
                    // Weekly Plan (SUBS)
                    val weeklyPlan = products.find { it.productId == BillingManager.WEEKLY_PLAN_ID }
                    weeklyPlan?.let { plan ->
                        val price = plan.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "Rp 4.900"
                        SubscriptionCard(
                            title = "Mingguan",
                            price = price,
                            period = "/ minggu",
                            onClick = { billingManager.launchBillingFlow(activity, plan) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Monthly Plan (SUBS)
                    val monthlyPlan = products.find { it.productId == BillingManager.MONTHLY_PLAN_ID }
                    monthlyPlan?.let { plan ->
                        val price = plan.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "Rp 14.900"
                        SubscriptionCard(
                            title = "Bulanan",
                            price = price,
                            period = "/ bulan",
                            onClick = { billingManager.launchBillingFlow(activity, plan) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lifetime Plan (INAPP)
                    val lifetimePlan = products.find { it.productId == BillingManager.LIFETIME_PLAN_ID }
                    lifetimePlan?.let { plan ->
                        // In-app products use oneTimePurchaseOfferDetails instead of subscriptionOfferDetails
                        val price = plan.oneTimePurchaseOfferDetails?.formattedPrice ?: "Rp 49.000"
                        SubscriptionCard(
                            title = "Seumur Hidup",
                            price = price,
                            period = "sekali bayar",
                            isPopular = true, // Jadikan Lifetime sebagai opsi terpopuler
                            onClick = { billingManager.launchBillingFlow(activity, plan) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TextButton(
                    onClick = { billingManager.queryActivePurchases() },
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text("Pulihkan Pembelian (Restore)", color = Violet)
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SubscriptionCard(
    title: String,
    price: String,
    period: String,
    isPopular: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPopular) Violet.copy(alpha = 0.15f) else BgSurface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Violet, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TERBAIK",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = price,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " $period",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
