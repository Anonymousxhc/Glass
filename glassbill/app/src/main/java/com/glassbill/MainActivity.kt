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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.glassbill.domain.Party
import com.glassbill.domain.PartyRole
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
    var quality by remember { mutableStateOf(GlassQuality.Flat) }
    GlassBillTheme(quality = quality) {
        val backdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .layerBackdrop(backdrop),
        ) {
            DashboardContent(
                controller = controller,
                selectedDestination = selectedDestination,
                quality = quality,
                onQualityChanged = { quality = it },
                modifier = Modifier.fillMaxSize(),
            )
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
        if (selectedDestination == Destination.Parties) {
            PartyList(
                parties = parties,
                onAdd = { controller.addParty(it) },
                onDelete = { controller.deleteParty(it) },
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
private fun FloatingNavigation(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    selected: Destination,
    onSelected: (Destination) -> Unit,
    quality: GlassQuality,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        backdrop = backdrop,
        quality = quality,
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = 8.dp, vertical = 10.dp)),
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

@Composable
private fun FloatingNavigationItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "tab-${destination.name}",
    )
    Column(
        modifier = modifier
            .scale(scale)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
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