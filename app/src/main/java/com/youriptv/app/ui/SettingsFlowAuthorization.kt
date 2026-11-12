package com.youriptv.app.ui

/** One process-local Settings visit. A route or Intent extra alone is never an authorization. */
internal class SettingsFlowAuthorization(private val newTicket: () -> String = { java.util.UUID.randomUUID().toString() }) {
    enum class Destination { SETTINGS, PLAYLISTS, EDITOR }
    data class Route(val destination: Destination, val playlistId: Long = 0L)
    private data class Member(val route: Route, val parent: Any?)
    private sealed interface Handoff {
        val source: Any
        data class Forward(override val source: Any, val target: Route, val ticket: String) : Handoff
        data class Return(override val source: Any, val target: Any) : Handoff
    }

    private var generation = 0L
    private val members = mutableMapOf<Any, Member>()
    private var current: Any? = null
    private var handoff: Handoff? = null
    private var parentalPermission = false

    fun authenticate(owner: Any, route: Route) {
        revoke()
        members[owner] = Member(route, null)
        current = owner
    }

    fun token(owner: Any): Long? = generation.takeIf { current === owner && members.containsKey(owner) }
    fun isAuthorized(owner: Any, token: Long? = token(owner)): Boolean =
        token != null && token == generation && current === owner && members.containsKey(owner)
    fun retains(owner: Any) = members.containsKey(owner)
    fun hasParentalPermission(owner: Any) = isAuthorized(owner) && parentalPermission
    fun allowParental(owner: Any): Boolean {
        if (!isAuthorized(owner)) return false
        parentalPermission = true
        return true
    }

    /** Only a completed, current Settings authorization can create a forward capability. */
    fun forward(owner: Any, target: Route): String? {
        if (!isAuthorized(owner) || handoff != null) return null
        return newTicket().also { handoff = Handoff.Forward(owner, target, it) }
    }

    fun cancelForward(owner: Any) {
        if ((handoff as? Handoff.Forward)?.source === owner) handoff = null
    }

    /** Accept an exact one-use forward capability, or a return to the original RAM-owned parent. */
    fun enter(owner: Any, route: Route, ticket: String? = null): Boolean {
        val pending = handoff
        if (ticket != null) {
            if (pending !is Handoff.Forward || pending.ticket != ticket || pending.target != route || !members.containsKey(pending.source)) return false
            if (members.containsKey(owner)) return false
            handoff = null
            members[owner] = Member(route, pending.source)
            current = owner
            return true
        }
        if (pending is Handoff.Return && pending.target === owner && members[owner]?.route == route) {
            members.remove(pending.source)
            handoff = null
            current = owner
            return true
        }
        return current === owner && members[owner]?.route == route
    }

    fun returning(owner: Any) {
        if ((handoff as? Handoff.Return)?.source === owner) return
        if (!isAuthorized(owner)) return
        val parent = members[owner]?.parent
        if (parent != null && members.containsKey(parent)) handoff = Handoff.Return(owner, parent)
        else revoke()
    }

    /** A pause without an explicitly prepared internal transition ends the complete visit. */
    fun paused(owner: Any) {
        if (current !== owner) return
        if (handoff?.source === owner) current = null else revoke()
    }

    fun destroyed(owner: Any) {
        if (!members.containsKey(owner)) return
        if ((handoff as? Handoff.Return)?.source === owner) members.remove(owner)
        else revoke()
    }

    fun revoke() {
        generation++
        members.clear()
        current = null
        handoff = null
        parentalPermission = false
    }
}
