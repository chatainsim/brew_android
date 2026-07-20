package fr.easter.brewhome.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.BeerPut

/** Édition des champs de base d'une bière, avec photo (appareil / galerie). */
@Composable
fun BeerEditScreen(vm: BrewViewModel, beerId: Int?, onSaved: () -> Unit) {
    val state by vm.state.collectAsState()
    val beer = state.beers.find { it.id == beerId }
    if (beer == null) {
        EmptyHint(stringResource(R.string.beer_not_found))
        return
    }
    val context = LocalContext.current

    var name by rememberSaveable { mutableStateOf(beer.name) }
    var type by rememberSaveable { mutableStateOf(beer.type ?: "") }
    var abv by rememberSaveable { mutableStateOf(beer.abv?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var origin by rememberSaveable { mutableStateOf(beer.origin ?: "") }
    var description by rememberSaveable { mutableStateOf(beer.description ?: "") }
    var brewDate by rememberSaveable { mutableStateOf(beer.brewDate ?: "") }
    var bottlingDate by rememberSaveable { mutableStateOf(beer.bottlingDate?.take(10) ?: "") }
    var referm by rememberSaveable { mutableStateOf((beer.refermentation ?: 0) == 1) }
    var refermDays by rememberSaveable { mutableStateOf(beer.refermentationDays?.toString() ?: "") }
    // null = photo inchangée (on repassera beer.photo) ; sinon nouveau data URL
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bmp -> if (bmp != null) newPhoto = ImageUpload.bitmapToDataUrl(bmp) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) ImageUpload.uriToBitmap(context, uri)?.let { newPhoto = ImageUpload.bitmapToDataUrl(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Photo : nouvelle si choisie, sinon la photo actuelle de la bière
        var photoMenu by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                )
                .clickable { photoMenu = true },
            contentAlignment = Alignment.Center,
        ) {
            val model = newPhoto ?: vm.photoUrl(beer.photo)
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = photoMenu,
                onDismissRequest = { photoMenu = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_camera)) },
                    onClick = { photoMenu = false; cameraLauncher.launch(null) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_gallery)) },
                    onClick = {
                        photoMenu = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
        }

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.label_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = type, onValueChange = { type = it },
                label = { Text(stringResource(R.string.beer_field_type)) },
                singleLine = true, modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = abv, onValueChange = { abv = it },
                label = { Text(stringResource(R.string.beer_field_abv)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = origin, onValueChange = { origin = it },
            label = { Text(stringResource(R.string.beer_field_origin)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = brewDate, onValueChange = { brewDate = it },
                label = { Text(stringResource(R.string.beer_brew_date)) },
                placeholder = { Text("2026-08-01") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = bottlingDate, onValueChange = { bottlingDate = it },
                label = { Text(stringResource(R.string.beer_bottling_date)) },
                placeholder = { Text("2026-08-15") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.beer_referm),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (referm) {
                OutlinedTextField(
                    value = refermDays, onValueChange = { refermDays = it },
                    label = { Text(stringResource(R.string.beer_referm_days)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Switch(checked = referm, onCheckedChange = { referm = it })
        }
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text(stringResource(R.string.beer_field_desc)) },
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                saving = true
                vm.saveBeer(beer.id, BeerPut(
                    name = name.trim(),
                    type = type.trim().ifBlank { null },
                    abv = abv.trim().replace(',', '.').toDoubleOrNull(),
                    stock33 = beer.stock33 ?: 0,
                    stock75 = beer.stock75 ?: 0,
                    kegLiters = beer.kegLiters,
                    origin = origin.trim().ifBlank { null },
                    description = description.trim().ifBlank { null },
                    // Photo inchangée : on repasse l'URL existante pour la conserver
                    photo = newPhoto ?: beer.photo,
                    brewDate = brewDate.trim().ifBlank { null },
                    bottlingDate = bottlingDate.trim().ifBlank { null },
                    refermentation = if (referm) 1 else 0,
                    refermentationDays = refermDays.trim().toIntOrNull(),
                )) { onSaved() }
                saving = false
            },
            enabled = name.isNotBlank() && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.save), Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}
