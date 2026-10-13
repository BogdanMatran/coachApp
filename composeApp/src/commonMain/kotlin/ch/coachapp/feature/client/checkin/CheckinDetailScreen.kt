package ch.coachapp.feature.client.checkin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.underBarsInset
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CheckinDetailRoute(
    checkinId: String,
    onBack: () -> Unit,
    viewModel: CheckinDetailViewModel = koinViewModel(key = "checkin-$checkinId") { parametersOf(checkinId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckinDetailScreen(state, onBack = onBack, onRetry = viewModel::load)
}

@Composable
fun CheckinDetailScreen(state: CheckinDetailUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    Scaffold(
        topBar = { BackTopBar("Check-in", onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val checkin = state.checkin
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error, onRetry)
                checkin != null -> Centered {
                    CheckinReadOnly(
                        checkin = checkin,
                        previous = state.previous,
                        questions = state.questions,
                        photoUrls = state.photoUrls,
                        previousPhotoUrls = state.previousPhotoUrls,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + underBarsInset()),
                    )
                }
            }
        }
    }
}
