package ch.coachapp.di

import ch.coachapp.core.createAppSupabaseClient
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.CoachRepository
import ch.coachapp.data.repository.LibraryRepository
import ch.coachapp.data.repository.LogRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.data.repository.SupabaseAuthRepository
import ch.coachapp.data.repository.SupabaseCheckinRepository
import ch.coachapp.data.repository.SupabaseCoachRepository
import ch.coachapp.data.repository.SupabaseLibraryRepository
import ch.coachapp.data.repository.SupabaseLogRepository
import ch.coachapp.data.repository.SupabasePlanRepository
import ch.coachapp.feature.auth.AuthViewModel
import ch.coachapp.feature.client.clientFeatureModule
import ch.coachapp.feature.coach.coachFeatureModule
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dataModule = module {
    single<SupabaseClient> { createAppSupabaseClient() }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<AuthRepository> { SupabaseAuthRepository(get(), get()) }
    single<CoachRepository> { SupabaseCoachRepository(get()) }
    single<LibraryRepository> { SupabaseLibraryRepository(get()) }
    single<PlanRepository> { SupabasePlanRepository(get()) }
    single<LogRepository> { SupabaseLogRepository(get()) }
    single<CheckinRepository> { SupabaseCheckinRepository(get()) }
}

val authModule = module {
    viewModelOf(::AuthViewModel)
}

val appModules = listOf(dataModule, authModule, coachFeatureModule, clientFeatureModule)
