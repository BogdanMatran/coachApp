package ch.coachapp.ios

import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.NativeChrome

/** What the SwiftUI navigation bar shows for one screen. */
data class ChromeState(val title: String, val items: List<ChromeItem>, val interceptBack: Boolean)

data class ChromeItem(
    val id: String,
    val label: String,
    val sfSymbol: String?,
    val enabled: Boolean,
    /** False for plain labels such as "3/10 sets". */
    val tappable: Boolean,
)

/**
 * One per native screen. Compose reports the title and bar actions here; SwiftUI renders
 * them and calls [trigger] / [back], which run the latest Kotlin callbacks.
 */
class ScreenHost : NativeChrome {
    private var state: ChromeState? = null
    private var handlers: Map<String, () -> Unit> = emptyMap()
    private var backHandler: (() -> Unit)? = null

    var onChange: ((ChromeState) -> Unit)? = null
        set(value) {
            field = value
            state?.let { value?.invoke(it) }
        }

    override fun update(title: String, actions: List<BarAction>, interceptBack: Boolean, onBack: (() -> Unit)?) {
        // Callbacks change on every recomposition; only push to Swift when what is shown changes.
        handlers = actions.mapNotNull { action -> action.onClick?.let { action.id to it } }.toMap()
        backHandler = onBack
        val next = ChromeState(
            title = title,
            items = actions.map { ChromeItem(it.id, it.label, it.sfSymbol, it.enabled, it.onClick != null) },
            interceptBack = interceptBack,
        )
        if (next != state) {
            state = next
            onChange?.invoke(next)
        }
    }

    fun trigger(id: String) {
        handlers[id]?.invoke()
    }

    fun back() {
        backHandler?.invoke()
    }
}
