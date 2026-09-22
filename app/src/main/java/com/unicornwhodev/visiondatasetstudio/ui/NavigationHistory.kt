package com.unicornwhodev.visiondatasetstudio.ui

/** Small deterministic history shared by Android Back and in-app back buttons. */
class NavigationHistory<T>(initial:T) {
    private val history=ArrayDeque<T>()
    var current:T=initial
        private set
    fun navigate(destination:T) {
        if(destination==current)return
        history.removeAll { it==destination }
        history.addLast(current)
        current=destination
    }
    fun back():T {
        current=history.removeLastOrNull() ?: current
        return current
    }
    fun replace(destination:T) { current=destination }
    fun reset(destination:T) { history.clear();current=destination }
    val canGoBack get()=history.isNotEmpty()
}
