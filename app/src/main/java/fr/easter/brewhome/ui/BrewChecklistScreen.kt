package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.ChecklistTemplate

private val checklistPhases = listOf(
    "preparation", "empatage", "ebullition", "refroidissement",
    "fermentation", "embouteillage", "autre",
)

@Composable
private fun phaseLabel(phase: String?): String = stringResource(
    when (phase) {
        "preparation" -> R.string.phase_preparation
        "empatage" -> R.string.phase_mash
        "ebullition" -> R.string.phase_boil
        "refroidissement" -> R.string.phase_cool
        "fermentation" -> R.string.phase_ferment
        "embouteillage" -> R.string.phase_bottling
        else -> R.string.phase_other
    },
)

/** Checklist de brassage : choix d'un modèle, cases à cocher par phase. */
@Composable
fun BrewChecklistScreen(vm: BrewViewModel, brewId: Int?) {
    if (brewId == null) return
    val templates by vm.checklistTemplates.collectAsState()
    val saved by vm.brewChecklist.collectAsState()
    LaunchedEffect(brewId) { vm.loadChecklist(brewId) }

    var creating by remember { mutableStateOf(false) }

    if (templates == null || saved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val tpls = templates!!
    // Modèle sélectionné : celui enregistré, sinon le premier disponible
    var templateId by remember(saved, tpls) {
        mutableStateOf(saved!!.templateId ?: tpls.firstOrNull()?.id)
    }
    var checked by remember(saved) { mutableStateOf(saved!!.checkedItems.toSet()) }

    val template = tpls.find { it.id == templateId }
    val items = template?.parsedItems().orEmpty()

    fun persist(newChecked: Set<String>) {
        checked = newChecked
        vm.saveChecklist(brewId, templateId, newChecked.toList())
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (tpls.isEmpty()) {
            EmptyHint(stringResource(R.string.checklist_no_template), Icons.Filled.Add)
            TextButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.checklist_new_template))
            }
        } else {
            TemplatePicker(
                templates = tpls,
                selectedId = templateId,
                onSelect = { id ->
                    templateId = id
                    // On conserve les cases cochées dont l'id existe encore
                    val validIds = tpls.find { it.id == id }?.parsedItems()?.map { it.id }?.toSet() ?: emptySet()
                    persist(checked.intersect(validIds))
                },
                onNew = { creating = true },
                onDelete = { vm.deleteChecklistTemplate(it) },
            )
            val doneCount = items.count { it.id in checked }
            if (items.isNotEmpty()) {
                Text(
                    stringResource(R.string.checklist_progress, doneCount, items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else doneCount.toFloat() / items.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val byPhase = items.groupBy { it.phase ?: "autre" }
                checklistPhases.filter { byPhase.containsKey(it) }.forEach { phase ->
                    item(key = "ph_$phase") {
                        Text(
                            phaseLabel(phase),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(byPhase.getValue(phase), key = { it.id }) { it0 ->
                        val isChecked = it0.id in checked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    persist(if (isChecked) checked - it0.id else checked + it0.id)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = {
                                persist(if (isChecked) checked - it0.id else checked + it0.id)
                            })
                            Text(
                                it0.text,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                                color = if (isChecked) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NewTemplateDialog(
            onDismiss = { creating = false },
            onCreate = { name, texts -> vm.createChecklistTemplate(name, null, texts) { creating = false } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePicker(
    templates: List<ChecklistTemplate>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onDelete: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = templates.find { it.id == selectedId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selected?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.checklist_template)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                templates.forEach { tpl ->
                    DropdownMenuItem(
                        text = { Text(tpl.name) },
                        onClick = { onSelect(tpl.id); expanded = false },
                        trailingIcon = {
                            IconButton(onClick = { onDelete(tpl.id); expanded = false }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        },
                    )
                }
            }
        }
        IconButton(onClick = onNew) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.checklist_new_template))
        }
    }
}

@Composable
private fun NewTemplateDialog(onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checklist_new_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.checklist_template_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.checklist_items_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && body.isNotBlank(),
                onClick = {
                    val texts = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    onCreate(name.trim(), texts)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
