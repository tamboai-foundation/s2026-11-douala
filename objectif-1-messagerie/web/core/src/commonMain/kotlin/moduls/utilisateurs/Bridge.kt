@file:OptIn(ExperimentalJsExport::class)

package ai.tambo.foundation.ngoma.moduls.utilisateurs

import ai.tambo.foundation.ngoma.NGomaCore
import ai.tambo.foundation.ngoma.models.publications.Post
import ai.tambo.foundation.ngoma.models.utilisateurs.Session
import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.foundation.ngoma.moduls.publications.deletePostCascade
import ai.tambo.tmodeler.model.TModel
import ai.tambo.tmodeler.ms.Tms
import ai.tambo.tmodeler.ms.ext.ibuild
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

// =============================================================================
// Helpers JSON (partagés avec les modules conversations et publications)
// =============================================================================

internal fun esc(s: String): String = buildString {
    for (c in s) when (c) {
        '"'  -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c.code < 0x20) append("\\u${c.code.toString(16).padStart(4, '0')}") else append(c)
    }
}

internal fun js(v: Any?): String = when (v) {
    null       -> "null"
    is Boolean -> v.toString()
    is Number  -> v.toString()
    else       -> "\"${esc(v.toString())}\""
}

internal fun obj(vararg fields: Pair<String, Any?>): String =
    fields.joinToString(",", "{", "}") { "\"${it.first}\":${js(it.second)}" }

internal fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

internal fun error(code: String): String = "{\"error\":\"$code\"}"

internal inline fun <R> n(f: () -> R?): R? = try { f() } catch (_: NullPointerException) { null }

internal fun <T : TModel<T>> all(tms: Tms<T>): List<T> = tms.with(tms.lambda).ibuild()

internal fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun checkReady(): String? = if (!NGomaCore.ready_) error("NOT_READY") else null

private fun findUser(userId: String): User? {
    val uid = userId.toLongOrNull() ?: return null
    return all(User.tms).firstOrNull { it._id_ == uid }
}

// =============================================================================
// Inscription, connexion, session
// =============================================================================

/**
 * Inscription.
 * RG-U1 : nom, identifiant (téléphone ou e-mail) et mot de passe obligatoires.
 * RG-U2 : identifiant unique.
 * RG-U3 : nom entre 2 et 50 caractères.
 * RG-U4 : le core ne reçoit que l'empreinte du mot de passe, calculée côté React.
 * RG-U7 : joinedAt fixé à la création.
 */
@JsExport
@JsName("ngomaRegister")
fun ngomaRegister(name: String, identifier: String, passwordHash: String): String {
    checkReady()?.let { return it }
    val nm = name.trim()
    val id = identifier.trim()
    if (nm.isEmpty())           return error("NAME_REQUIRED")
    if (nm.length < 2)          return error("NAME_TOO_SHORT")
    if (nm.length > 50)         return error("NAME_TOO_LONG")
    if (id.isEmpty())           return error("PHONE_REQUIRED")
    if (passwordHash.isBlank()) return error("PASSWORD_REQUIRED")
    return try {
        if (all(User.tms).any { it.identifier_ == id }) return error("PHONE_ALREADY_USED")  // RG-U2
        val user = User().apply {
            name_         = nm
            identifier_   = id
            passwordHash_ = passwordHash
            bio_          = ""
            joinedAt_     = now()
            deleted_      = false
        }
        user.isave()
        openSession(user)
        userJson(user)
    } catch (_: Throwable) { error("REGISTER_FAILED") }
}

/**
 * Connexion.
 * RG-U5 : l'identifiant doit correspondre à un compte non supprimé et l'empreinte du mot de passe être identique.
 */
@JsExport
@JsName("ngomaLogin")
fun ngomaLogin(identifier: String, passwordHash: String): String {
    checkReady()?.let { return it }
    return try {
        val user = all(User.tms).firstOrNull {
            !it.deleted_ && it.identifier_ == identifier.trim() && it.passwordHash_ == passwordHash
        } ?: return error("INVALID_CREDENTIALS")
        openSession(user)
        userJson(user)
    } catch (_: Throwable) { error("LOGIN_FAILED") }
}

/** Restaure la session enregistrée (RG-U6). */
@JsExport
@JsName("ngomaRestoreSession")
fun ngomaRestoreSession(): String {
    checkReady()?.let { return it }
    return try {
        val session = all(Session.tms).firstOrNull() ?: return error("NO_SESSION")
        val user    = n { session.currentUser_ }      ?: return error("NO_SESSION")
        if (user.deleted_) return error("NO_SESSION")
        userJson(user)
    } catch (_: Throwable) { error("NO_SESSION") }
}

/** Déconnexion : vide la session (RG-U6). */
@JsExport
@JsName("ngomaLogout")
fun ngomaLogout(): String {
    checkReady()?.let { return it }
    return try {
        all(Session.tms).forEach { it.idelete() }
        obj("ok" to true)
    } catch (_: Throwable) { error("LOGOUT_FAILED") }
}

// =============================================================================
// Personnes et profil
// =============================================================================

/**
 * Liste des personnes (écran « Personnes »).
 * RG-U10 : tous les comptes non supprimés, y compris l'utilisateur connecté ;
 *          recherche sur le nom ou la description, sans tenir compte des majuscules.
 */
@JsExport
@JsName("ngomaUsers")
fun ngomaUsers(query: String): String {
    checkReady()?.let { return it }
    return try {
        val q = query.trim().lowercase()
        val list = all(User.tms)
            .filter { !it.deleted_ }
            .filter { q.isEmpty() || it.name_.lowercase().contains(q) || it.bio_.lowercase().contains(q) }
            .map { userJson(it) }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Recherche d'un contact pour ouvrir une conversation (fenêtre « Nouvelle conversation »).
 * Exclut l'utilisateur connecté et les comptes supprimés ; cherche dans le nom ou l'identifiant.
 */
@JsExport
@JsName("ngomaSearchUsers")
fun ngomaSearchUsers(query: String, currentUserId: String): String {
    checkReady()?.let { return it }
    return try {
        val uid = currentUserId.toLongOrNull() ?: return error("INVALID_ID")
        val q   = query.trim().lowercase()
        val list = all(User.tms)
            .filter { it._id_ != uid && !it.deleted_ }
            .filter { it.name_.lowercase().contains(q) || it.identifier_.lowercase().contains(q) }
            .map { userJson(it) }
        arr(list)
    } catch (_: Throwable) { error("SEARCH_FAILED") }
}

/** Profil d'un utilisateur (RG-U11). */
@JsExport
@JsName("ngomaGetUser")
fun ngomaGetUser(userId: String): String {
    checkReady()?.let { return it }
    return try {
        val user = findUser(userId) ?: return error("USER_NOT_FOUND")
        userJson(user)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Modifie le profil de l'utilisateur connecté : nom et description.
 * RG-U8 : un nom vide n'est pas enregistré (l'ancien est conservé).
 * RG-U3 : sinon, le nom comporte entre 2 et 50 caractères.
 */
@JsExport
@JsName("ngomaUpdateProfile")
fun ngomaUpdateProfile(userId: String, newName: String, newBio: String): String {
    checkReady()?.let { return it }
    val nm = newName.trim()
    if (nm.isNotEmpty() && nm.length < 2) return error("NAME_TOO_SHORT")
    if (nm.length > 50)                   return error("NAME_TOO_LONG")
    return try {
        val user = findUser(userId) ?: return error("USER_NOT_FOUND")
        if (user.deleted_) return error("USER_NOT_FOUND")
        if (nm.isNotEmpty()) user.name_ = nm
        user.bio_ = newBio.trim()
        user.isave()
        userJson(user)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}

/**
 * Supprime définitivement le compte (RG-U12).
 * Données personnelles effacées, identifiant libéré, session fermée,
 * publications supprimées (RG-P7) ; les messages restent dans les conversations des autres.
 */
@JsExport
@JsName("ngomaDeleteAccount")
fun ngomaDeleteAccount(userId: String): String {
    checkReady()?.let { return it }
    return try {
        val user = findUser(userId) ?: return error("USER_NOT_FOUND")
        all(Post.tms).filter { n { it.author_?._id_ } == user._id_ }.forEach { deletePostCascade(it) }
        user.name_         = "Compte supprimé"
        user.identifier_   = "deleted-${user._id_}"   // libère l'identifiant (unique, mais jamais saisi)
        user.passwordHash_ = ""
        user.bio_          = ""
        user.deleted_      = true
        user.isave()
        all(Session.tms).forEach { it.idelete() }
        obj("ok" to true)
    } catch (_: Throwable) { error("DELETE_FAILED") }
}

// =============================================================================
// Helpers privés
// =============================================================================

private fun openSession(user: User) {
    all(Session.tms).forEach { it.idelete() }
    Session().apply { currentUser_ = user }.isave()
}

internal fun userJson(user: User): String = obj(
    "id"         to user._id_.toString(),
    "name"       to user.name_,
    "identifier" to user.identifier_,
    "phone"      to user.identifier_,   // alias lu par la fenêtre « Nouvelle conversation »
    "bio"        to user.bio_,
    "joinedAt"   to (n { user.joinedAt_.toString() } ?: ""),
    "deleted"    to user.deleted_,
)
