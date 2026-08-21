package com.gabrielliz.translatedecria.accessibility

import java.lang.ref.WeakReference

/**
 * Flavor-neutral bridge used by MainActivity.
 *
 * The Safe flavor contains this bridge but does not package an AccessibilityService implementation.
 */
object AccessibilityControl {
    interface Session {
        fun startTranslation()
        fun stopTranslation()
    }

    @Volatile
    private var sessionRef: WeakReference<Session>? = null

    fun attach(session: Session) {
        sessionRef = WeakReference(session)
    }

    fun detach(session: Session) {
        if (sessionRef?.get() === session) sessionRef = null
    }

    fun requestStart(): Boolean = sessionRef?.get()?.let {
        it.startTranslation()
        true
    } ?: false

    fun requestStop() {
        sessionRef?.get()?.stopTranslation()
    }
}
