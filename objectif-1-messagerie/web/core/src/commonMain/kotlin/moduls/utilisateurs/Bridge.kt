@file:OptIn(ExperimentalJsExport::class)

package ai.tambo.foundation.ngoma.moduls.utilisateurs

import ai.tambo.foundation.ngoma.NGomaCore
import ai.tambo.foundation.ngoma.models.utilisateurs.Session
import ai.tambo.foundation.ngoma.models.utilisateurs.User
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
// Helpers JSON (dupliqués par module pour garder chaque module indépendant)
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

private fun checkReady(): String? = if (!NGomaCore.ready_) error("NOT_READY") else null

// =============================================================================
// Auth
// =============================================================================

/**
 * Inscription.
 * RG-U1 : nom, téléphone et mot de passe obligatoires.
 * RG-U2 : téléphone unique.
 * RG-U3 : nom entre 2 et 50 caractères.
 * RG-U4 : mot de passe ≥ 8 caractères (le passwordHash reçu est déjà haché côté React).
 * RG-U7 : createdAt fixé à la création.
 */
@JsExport
@JsName("ngomaRegister")
fun ngomaRegister(name: String, phone: String, passwordHash: String): String {
    checkReady()?.let { return it }
    if (name.isBlank())                    return error("NAME_REQUIRED")
    if (name.trim().length < 2)            return error("NAME_TOO_SHORT")
    if (name.trim().length > 50)           return error("NAME_TOO_LONG")
    if (phone.isBlank())                   return error("PHONE_REQUIRED")
    if (passwordHash.isBlank())            return error("PASSWORD_REQUIRED")
    return try {
        // RG-U2 : unicité du téléphone
        if (all(User.tms).any { it.phone_ == phone.trim() }) return error("PHONE_ALREADY_USED")

        val user = User().apply {
            name_         = name.trim()
            phone_        = phone.trim()
            passwordHash_ = passwordHash
            active_       = true
            createdAt_    = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
        user.isave()
        openSession(user)
        userJson(user)
    } catch (_: Throwable) { error("REGISTER_FAILED") }
}

/**
 * Connexion.
 * RG-U5 : réussit seulement si le téléphone correspond à un compte actif
 *          et que le passwordHash correspond.
 */
@JsExport
@JsName("ngomaLogin")
fun ngomaLogin(phone: String, passwordHash: String): String {
    checkReady()?.let { return it }
    return try {
        val user = all(User.tms).firstOrNull {
            it.phone_ == phone.trim() && it.passwordHash_ == passwordHash
        } ?: return error("INVALID_CREDENTIALS")
        // RG-U5 : compte actif
        if (!user.active_) return error("ACCOUNT_DISABLED")
        openSession(user)
        userJson(user)
    } catch (_: Throwable) { error("LOGIN_FAILED") }
}

/**
 * Restaure la session persistée (RG-U6).
 * Retourne {"id":"...","name":"..."} ou {"error":"NO_SESSION"}.
 */
@JsExport
@JsName("ngomaRestoreSession")
fun ngomaRestoreSession(): String {
    checkReady()?.let { return it }
    return try {
        val session = all(Session.tms).firstOrNull() ?: return error("NO_SESSION")
        val user    = n { session.currentUser_ }      ?: return error("NO_SESSION")
        if (!user.active_) return error("ACCOUNT_DISABLED")
        userJson(user)
    } catch (_: Throwable) { error("NO_SESSION") }
}

/**
 * Déconnexion : vide Session.currentUser (RG-U6).
 */
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
// Profil
// =============================================================================

/**
 * Retourne le profil d'un utilisateur par son id.
 */
@JsExport
@JsName("ngomaGetUser")
fun ngomaGetUser(userId: String): String {
    checkReady()?.let { return it }
    return try {
        val uid  = userId.toLongOrNull() ?: return error("INVALID_ID")
        val user = all(User.tms).firstOrNull { it._id_ == uid } ?: return error("USER_NOT_FOUND")
        userJson(user)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Modifie le profil de l'utilisateur connecté.
 * RG-U8 : un utilisateur ne peut modifier que son propre profil.
 * RG-U2 : nouveau téléphone revérifié.
 * RG-U3 : nom entre 2 et 50 caractères.
 */
@JsExport
@JsName("ngomaUpdateProfile")
fun ngomaUpdateProfile(userId: String, newName: String, newPhone: String, newPasswordHash: String): String {
    checkReady()?.let { return it }
    if (newName.trim().length < 2)  return error("NAME_TOO_SHORT")
    if (newName.trim().length > 50) return error("NAME_TOO_LONG")
    if (newPhone.isBlank())         return error("PHONE_REQUIRED")
    return try {
        val uid  = userId.toLongOrNull() ?: return error("INVALID_ID")
        val user = all(User.tms).firstOrNull { it._id_ == uid } ?: return error("USER_NOT_FOUND")

        // RG-U2 : unicité du nouveau téléphone (sauf si c'est le sien)
        if (newPhone.trim() != user.phone_) {
            if (all(User.tms).any { it.phone_ == newPhone.trim() }) return error("PHONE_ALREADY_USED")
        }
        user.name_         = newName.trim()
        user.phone_        = newPhone.trim()
        if (newPasswordHash.isNotBlank()) user.passwordHash_ = newPasswordHash
        user.isave()
        userJson(user)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}

/**
 * Désactive ou réactive un compte (RG-U10).
 * active = false → désactivé ; active = true → réactivé.
 */
@JsExport
@JsName("ngomaSetActive")
fun ngomaSetActive(userId: String, active: Boolean): String {
    checkReady()?.let { return it }
    return try {
        val uid  = userId.toLongOrNull() ?: return error("INVALID_ID")
        val user = all(User.tms).firstOrNull { it._id_ == uid } ?: return error("USER_NOT_FOUND")
        user.active_ = active
        user.isave()
        // Si désactivation → fermer la session
        if (!active) all(Session.tms).forEach { it.idelete() }
        obj("ok" to true)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}

// =============================================================================
// Recherche
// =============================================================================

/**
 * Recherche d'utilisateurs par nom ou téléphone.
 * RG-U9 : exclut l'utilisateur connecté et les comptes désactivés.
 */
@JsExport
@JsName("ngomaSearchUsers")
fun ngomaSearchUsers(query: String, currentUserId: String): String {
    checkReady()?.let { return it }
    return try {
        val uid = currentUserId.toLongOrNull() ?: return error("INVALID_ID")
        val q   = query.trim().lowercase()
        val list = all(User.tms)
            .filter { u ->
                u._id_   != uid &&
                u.active_        &&
                (u.name_.lowercase().contains(q) || u.phone_.contains(q))
            }
            .map { userJson(it) }
        arr(list)
    } catch (_: Throwable) { error("SEARCH_FAILED") }
}

/**
 * Cherche un utilisateur par son téléphone exact.
 * Utilisé pour vérifier qu'un interlocuteur existe avant de créer une conversation.
 */
@JsExport
@JsName("ngomaFindUserByPhone")
fun ngomaFindUserByPhone(phone: String): String {
    checkReady()?.let { return it }
    return try {
        val user = all(User.tms).firstOrNull { it.phone_ == phone.trim() && it.active_ }
            ?: return error("USER_NOT_FOUND")
        userJson(user)
    } catch (_: Throwable) { error("FIND_FAILED") }
}

// =============================================================================
// Helpers privés
// =============================================================================

private fun openSession(user: User) {
    all(Session.tms).forEach { it.idelete() }
    Session().apply { currentUser_ = user }.isave()
}

private fun userJson(user: User): String = obj(
    "id"        to user._id_.toString(),
    "name"      to user.name_,
    "phone"     to user.phone_,
    "active"    to user.active_,
    "createdAt" to (n { user.createdAt_?.toString() } ?: "")
)
