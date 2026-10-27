package com.glassbill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glassbill.data.CompanySummary
import com.glassbill.domain.CompanyId
import com.glassbill.domain.CatalogItem
import com.glassbill.domain.ItemKind
import com.glassbill.domain.Party
import com.glassbill.domain.PartyRole
import com.glassbill.domain.Voucher
import com.glassbill.domain.VoucherLine
import java.math.BigDecimal
import kotlinx.coroutines.launch
import com.glassbill.ui.GlassBillTheme
import com.glassbill.ui.GlassQuality
import com.glassbill.ui.GlassSurface
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlassBillApp(this) }
    }
}

private enum class Destination(val label: String) {
    Dashboard("Dashboard"),
    Items("Items"),
    Parties("Parties"),
    Bills("Bills"),
    Reports("Reports"),
}

@Composable
private fun GlassBillApp(activity: MainActivity) {
    val controller = remember { GlassBillController(activity) }
    var selectedDestination by remember { mutableStateOf(Destination.Dashboard) }
    var quality by remember { mutableStateOf(GlassQuality.Full) }
    GlassBillTheme(quality = quality) {
        val surfaceColor = MaterialTheme.colorScheme.background
        val backdrop = rememberLayerBackdrop(onDraw = {
            drawRect(surfaceColor)
            drawContent()
        })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                DashboardContent(
                    controller = controller,
                    selectedDestination = selectedDestination,
                    quality = quality,
                    onQualityChanged = { quality = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            FloatingNavigation(
                backdrop = backdrop,
                selected = selectedDestination,
                onSelected = { selectedDestination = it },
                quality = quality,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun DashboardContent(
    controller: GlassBillController,
    selectedDestination: Destination,
    quality: GlassQuality,
    onQualityChanged: (GlassQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val companies by controller.companies.collectAsState()
    val currentId by controller.currentCompanyId.collectAsState()
    val parties by controller.parties.collectAsState()
    val profile by controller.profile.collectAsState()
    var showMore by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = selectedDestination.label,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        CompanySwitcher(
            companies = companies,
            currentId = currentId,
            onOpen = { controller.openCompany(it) },
            onCreate = { controller.createCompany(it) },
        )
        Text(
            text = profile?.displayName ?: "No business profile",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SummaryRow(companies = companies.size, parties = parties.size, items = 0)
        QualitySelector(selected = quality, onSelected = onQualityChanged)
        Text(
            text = "More features",
            modifier = Modifier.clickable { showMore = true },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        if (showMore) {
            MoreScreen(onBack = { showMore = false })
        } else if (selectedDestination == Destination.Parties) {
            PartyList(
                parties = parties,
                onAdd = { controller.addParty(it) },
                onDelete = { controller.deleteParty(it) },
            )
        }
        if (selectedDestination == Destination.Bills) {
            BillList(
                vouchers = controller.vouchers.collectAsState().value,
                parties = parties,
                items = controller.items.collectAsState().value,
                onCreateVoucher = { k, party, lines -> controller.createVoucher(k, party, lines) },
                onDelete = { controller.deleteVoucher(it) },
            )
        }
        if (selectedDestination == Destination.Reports) {
            ReportList()
        }
        if (selectedDestination == Destination.Items) {
            ItemList(
                items = controller.items.collectAsState().value,
                units = controller.units.collectAsState().value,
                taxRates = controller.taxRates.collectAsState().value,
                onAdd = { controller.addItem(it) },
            )
        }
    }
}

@Composable
private fun CompanySwitcher(
    companies: List<CompanySummary>,
    currentId: CompanyId?,
    onOpen: (CompanyId) -> Unit,
    onCreate: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MiuixSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Company", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = companies.firstOrNull { it.id == currentId }?.displayName ?: "None",
                        modifier = Modifier
                            .weight(1f)
                            .clickable { expanded = !expanded },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (expanded) {
                    companies.forEach { c ->
                        Text(
                            text = c.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(c.id); expanded = false }
                                .padding(vertical = 4.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("New company") },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Create",
                            modifier = Modifier
                                .clickable {
                                    if (newName.isNotBlank()) {
                                        onCreate(newName.trim())
                                        newName = ""
                                        expanded = false
                                    }
                                }
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(companies: Int, parties: Int, items: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard("Companies", companies.toString(), Modifier.weight(1f))
        SummaryCard("Parties", parties.toString(), Modifier.weight(1f))
        SummaryCard("Items", items.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    MiuixSurface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QualitySelector(selected: GlassQuality, onSelected: (GlassQuality) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassQuality.entries.forEach { quality ->
                val active = quality == selected
                val scale by animateFloatAsState(
                    targetValue = if (active) 1.03f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                    label = "quality-$quality",
                )
                Text(
                    text = quality.name,
                    modifier = Modifier
                        .scale(scale)
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(role = Role.RadioButton) { onSelected(quality) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PartyList(
    parties: List<Party>,
    onAdd: (Party) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(PartyRole.Customer) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Parties", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Party name") },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = role.name,
                modifier = Modifier
                    .clickable {
                        role = when (role) {
                            PartyRole.Customer -> PartyRole.Supplier
                            PartyRole.Supplier -> PartyRole.Both
                            PartyRole.Both -> PartyRole.Customer
                        }
                    }
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Add",
                modifier = Modifier
                    .clickable {
                        if (name.isNotBlank()) {
                            onAdd(Party(id = System.currentTimeMillis().toString(), name = name.trim(), role = role))
                            name = ""
                        }
                    }
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(parties, key = { it.id }) { party ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(party.name)
                    Text(party.role.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Delete",
                        modifier = Modifier.clickable { onDelete(party.id) },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BillList(
    vouchers: List<Voucher>,
    parties: List<Party>,
    items: List<CatalogItem>,
    onCreateVoucher: (com.glassbill.domain.DocumentKind, Party?, List<VoucherLine>) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("Sale") }
    var itemName by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("0") }
    var partyName by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<CatalogItem?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Add Voucher",
            modifier = Modifier
                .clickable { showDialog = true }
                .padding(8.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Text("History: ${vouchers.size} vouchers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(vouchers, key = { it.id }) { v ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(v.number)
                    Text(v.kind.name)
                    Text(v.partyName.ifBlank { "Cash" })
                    Text(v.totals(false).totalAmount.toPlainString())
                    Text(
                        text = "Delete",
                        modifier = Modifier.clickable { onDelete(v.id) },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Text(
                    "Save",
                    modifier = Modifier.clickable {
                        val q = qty.toBigDecimalOrNull() ?: BigDecimal.ONE
                        val p = price.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val name = selectedItem?.name ?: itemName.ifBlank { "Item" }
                        val line = VoucherLine(
                            itemId = selectedItem?.id,
                            itemName = name,
                            quantity = q,
                            unit = "pcs",
                            unitPrice = p,
                        )
                        val party = parties.firstOrNull { it.name == partyName }
                        val k = if (kind == "Sale") com.glassbill.domain.DocumentKind.Sale else com.glassbill.domain.DocumentKind.Purchase
                        onCreateVoucher(k, party, listOf(line))
                        showDialog = false
                        itemName = ""
                        qty = "1"
                        price = "0"
                        partyName = ""
                        selectedItem = null
                    },
                )
            },
            dismissButton = {
                Text("Cancel", modifier = Modifier.clickable { showDialog = false })
            },
            title = { Text("New $kind") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Sale", "Purchase").forEach { k ->
                            Text(
                                text = k,
                                modifier = Modifier
                                    .clickable { kind = k }
                                    .background(
                                        if (kind == k) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(999.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                color = if (kind == k) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        Text("Pick item:", style = MaterialTheme.typography.labelMedium)
                        items.take(5).forEach { it ->
                            Text(
                                text = it.name,
                                modifier = Modifier
                                    .clickable { selectedItem = it; itemName = it.name; price = (it.salePriceMinor?.toString() ?: "0") }
                                    .padding(4.dp),
                                color = if (selectedItem?.id == it.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    TextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item name") })
                    TextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") })
                    TextField(value = price, onValueChange = { price = it }, label = { Text("Price") })
                    TextField(value = partyName, onValueChange = { partyName = it }, label = { Text("Party (optional)") })
                }
            },
        )
    }
}

@Composable
private fun ItemList(
    items: List<CatalogItem>,
    units: List<com.glassbill.domain.MeasurementUnit>,
    taxRates: List<com.glassbill.domain.TaxRate>,
    onAdd: (CatalogItem) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = name, onValueChange = { name = it }, label = { Text("Item name") }, modifier = Modifier.weight(1f))
            Text(
                text = "Add",
                modifier = Modifier
                    .clickable {
                        if (name.isNotBlank()) {
                            onAdd(CatalogItem(id = System.currentTimeMillis().toString(), name = name.trim(), itemKind = ItemKind.Goods))
                            name = ""
                        }
                    }
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.id }) { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.name)
                    Text(item.itemKind.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReportList() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        listOf("Sales Summary", "Purchase Summary", "Party Statement", "Stock Report", "Profit & Loss", "GST Summary").forEach { name ->
            Text(name, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun MoreScreen(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Back", modifier = Modifier.clickable { onBack() }, color = MaterialTheme.colorScheme.primary)
        Text("More features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        listOf(
            "M3: Party Statement", "M3: Sales/Purchase Report", "M3: Stock Report",
            "M3: Profit & Loss", "M3: GST Summary",
            "M4: Reminders", "M4: Loyalty Points", "M4: Multi-Currency", "M4: TDS/TCS",
            "M5: Manufacturing", "M5: Fixed Assets", "M5: Bank Statement Import",
            "M5: Voice Entry", "M5: Recycle Bin", "M5: Fiscal Year", "M5: Backup & Restore",
        ).forEach { name ->
            Text(name, modifier = Modifier.padding(vertical = 2.dp))
        }
        Text("These modules are scaffolded; full implementation follows per milestone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FloatingNavigation(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    selected: Destination,
    onSelected: (Destination) -> Unit,
    quality: GlassQuality,
    modifier: Modifier = Modifier,
) {
    var barWidth by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tabCount = Destination.entries.size
    val tabWidthPx = if (tabCount > 0 && barWidth > 0) barWidth / tabCount else 0
    val selectedIndex = Destination.entries.indexOf(selected)
    var isDragging by remember { mutableStateOf(false) }
    val dragScope = rememberCoroutineScope()
    val indicatorOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(selectedIndex, tabWidthPx) {
        if (tabWidthPx > 0 && !isDragging) {
            indicatorOffset.animateTo(
                selectedIndex * tabWidthPx.toFloat(),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }
    GlassSurface(
        backdrop = backdrop,
        quality = quality,
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 8.dp, vertical = 10.dp))
                .onSizeChanged { barWidth = it.width }
                .pointerInput(tabWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onHorizontalDrag = { _, dragAmount ->
                            dragScope.launch {
                                indicatorOffset.snapTo((indicatorOffset.value + dragAmount).coerceIn(0f, (tabCount - 1) * tabWidthPx.toFloat()))
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val nearest = (indicatorOffset.value / tabWidthPx).toInt().coerceIn(0, tabCount - 1)
                            onSelected(Destination.entries[nearest])
                        },
                        onDragCancel = { isDragging = false },
                    )
                },
        ) {
            // Sliding glass indicator pill
            if (tabWidthPx > 0) {
                GlassSurface(
                    backdrop = backdrop,
                    quality = quality,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .offset(x = with(density) { indicatorOffset.value.toDp() })
                        .width(with(density) { (tabWidthPx - 16).toDp() })
                        .height(44.dp)
                        .align(Alignment.CenterStart),
                ) {}
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { destination ->
                    FloatingNavigationItem(
                        destination = destination,
                        selected = destination == selected,
                        onClick = { onSelected(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.78f
            selected -> 1.2f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tab-${destination.name}",
    )
    Column(
        modifier = modifier
            .scale(scale)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 8.dp else 6.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}