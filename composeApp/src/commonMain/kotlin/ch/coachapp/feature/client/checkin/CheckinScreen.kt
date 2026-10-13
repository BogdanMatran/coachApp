package ch.coachapp.feature.client.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.Celebration
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.NumberField
import ch.coachapp.core.ui.NumberSegmentedControl
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.YesNoSegmentedControl
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.core.ui.highlightClickable
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.MeasurementKeys
import ch.coachapp.data.model.Pose
import ch.coachapp.data.model.QuestionType
import coil3.compose.AsyncImage
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** This week's check-in form, opened from the Check-in list; [onDone] runs once it's sent. */
@Composable
fun CheckinFormRoute(onBack: () -> Unit, onDone: () -> Unit, viewModel: CheckinViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pendingPose by remember { mutableStateOf<Pose?>(null) }
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        val pose = pendingPose
        pendingPose = null
        if (file != null && pose != null) {
            scope.launch { viewModel.setPhoto(pose, file.readBytes()) }
        }
    }
    Scaffold(topBar = { BackTopBar("Check-in", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CheckinScreen(
                state = state,
                onRetry = viewModel::load,
                onWeightChange = viewModel::setWeight,
                onBodyFatChange = viewModel::setBodyFat,
                onMeasurementChange = viewModel::setMeasurement,
                onAnswerChange = viewModel::setAnswer,
                onPickPhoto = { pose -> pendingPose = pose; picker.launch() },
                onRemovePhoto = viewModel::removeNewPhoto,
                onSubmit = viewModel::submit,
            )
        }
    }
    if (state.justSubmitted) {
        Celebration("Check-in sent", "Your coach will review it and reply here.", onFinished = onDone)
    }
}

@Composable
fun CheckinScreen(
    state: CheckinUiState,
    onRetry: () -> Unit,
    onWeightChange: (String) -> Unit,
    onBodyFatChange: (String) -> Unit,
    onMeasurementChange: (String, String) -> Unit,
    onAnswerChange: (String, String) -> Unit,
    onPickPhoto: (Pose) -> Unit,
    onRemovePhoto: (Pose) -> Unit,
    onSubmit: () -> Unit,
) {
    when {
        state.loading -> LoadingBox()
        state.error != null -> ErrorBox(state.error, onRetry)
        else -> Centered {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomBarInset()),
            ) {
                val current = state.current
                if (current != null && state.isReviewed) {
                    CheckinReadOnly(current, state.previous, state.questions, state.photoUrls, state.previousPhotoUrls)
                    Text(
                        "Your next check-in opens on ${Dates.dayName(Dates.weekStart())} next week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    CheckinFormContent(state, onWeightChange, onBodyFatChange, onMeasurementChange, onAnswerChange, onPickPhoto, onRemovePhoto, onSubmit)
                }
            }
        }
    }
}

@Composable
private fun CheckinFormContent(
    state: CheckinUiState,
    onWeightChange: (String) -> Unit,
    onBodyFatChange: (String) -> Unit,
    onMeasurementChange: (String, String) -> Unit,
    onAnswerChange: (String, String) -> Unit,
    onPickPhoto: (Pose) -> Unit,
    onRemovePhoto: (Pose) -> Unit,
    onSubmit: () -> Unit,
) {
    val form = state.form
    Text("Week of ${Dates.format(state.weekStart)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (state.current != null) {
        Text(
            "Submitted. You can still update it until your coach reviews it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.justSubmitted) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LocalAppColors.current.accText)
                Text("Check-in sent to your coach.", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    SectionHeader("Body")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(form.weight, onWeightChange, "Weight", Modifier.weight(1f), suffix = "kg")
        NumberField(form.bodyFat, onBodyFatChange, "Body fat", Modifier.weight(1f), suffix = "%")
    }
    SectionHeader("Measurements")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MeasurementKeys.forEach { key ->
            NumberField(
                value = form.measurements[key].orEmpty(),
                onValueChange = { onMeasurementChange(key, it) },
                label = key.replaceFirstChar { it.uppercase() },
                suffix = "cm",
                modifier = Modifier.width(150.dp),
            )
        }
    }

    if (state.questions.isNotEmpty()) {
        SectionHeader("Questions")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.questions.forEach { question ->
                QuestionInput(question, form.answers[question.id].orEmpty()) { onAnswerChange(question.id, it) }
            }
        }
    }

    SectionHeader("Progress photos")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pose.entries.forEach { pose ->
            PhotoSlot(
                pose = pose,
                newPhoto = form.newPhotos[pose],
                existingUrl = state.photoUrls[pose],
                onPick = { onPickPhoto(pose) },
                onRemove = { onRemovePhoto(pose) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    state.submitError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
    PrimaryButton(
        text = if (state.current == null) "Submit check-in" else "Update check-in",
        onClick = onSubmit,
        enabled = state.canSubmit,
        loading = state.submitting,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
    if (form.weight.isBlank()) {
        Text("Enter your weight to submit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One answer field, shaped by the question type. Shared with the onboarding form. */
@Composable
fun QuestionInput(question: CheckinQuestion, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (question.type != QuestionType.Text && question.type != QuestionType.Number) {
            Text(question.label, style = MaterialTheme.typography.bodyLarge)
        }
        when (question.type) {
            QuestionType.Scale -> NumberSegmentedControl(
                value = value.toIntOrNull(),
                onValueChange = { onChange(it.toString()) },
            )
            QuestionType.YesNo -> YesNoSegmentedControl(
                value = when (value) {
                    "yes" -> true
                    "no" -> false
                    else -> null
                },
                onValueChange = { onChange(if (it) "yes" else "no") },
            )
            QuestionType.Number -> NumberField(value, onChange, question.label, Modifier.fillMaxWidth())
            QuestionType.Text -> AppTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(question.label) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PhotoSlot(
    pose: Pose,
    newPhoto: ByteArray?,
    existingUrl: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant).highlightClickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            val model: Any? = newPhoto ?: existingUrl
            if (model != null) {
                AsyncImage(model = model, contentDescription = pose.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.AddCircle, contentDescription = "Add ${pose.label} photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (newPhoto != null) {
                FilledIconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) { Icon(Icons.Default.Close, contentDescription = "Remove photo") }
            }
        }
        Text(pose.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
