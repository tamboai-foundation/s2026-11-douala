@file:OptIn(ExperimentalJsExport::class)

package ai.tambo.foundation.ngoma.moduls.conversations

import ai.tambo.foundation.ngoma.NGomaCore
import ai.tambo.foundation.ngoma.models.conversations.Conversation
import ai.tambo.foundation.ngoma.models.conversations.ConversationMember
import ai.tambo.foundation.ngoma.models.conversations.Message
import ai.tambo.foundation.ngoma.models.conversations.Reaction
import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.foundation.ngoma.moduls.utilisateurs.all
import ai.tambo.foundation.ngoma.moduls.utilisateurs.arr
import ai.tambo.foundation.ngoma.moduls.utilisateurs.error
import ai.tambo.foundation.ngoma.moduls.utilisateurs.js
import ai.tambo.foundation.ngoma.moduls.utilisateurs.n
import ai.tambo.foundation.ngoma.moduls.utilisateurs.obj
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

private fun checkReady(): String? = if (!NGomaCore.ready_) error("NOT_READY") else null

// =============================================================================
// Conversations
// =============================================================================

/**
 * Retourne les conversations d'un utilisateur.
 * RG-C7 : épinglées en premier, puis par lastMessageAt décroissant.
 *         Les archivées sont masquées sauf si showArchived = true.
 */
@JsExport
@JsName("ngomaConversations")
fun ngomaConversations(userId: String, showArchived: Boolean): String {
    checkReady()?.let { return it }
    return try {
        val uid     = userId.toLongOrNull() ?: return error("INVALID_ID")
        val members = all(ConversationMember.tms)
            .filter { n { it.user_?._id_ } == uid }
            .filter { !it.hidden_ }   // conversations supprimées par cet utilisateur masquées
            .filter { if (showArchived) true else !it.archived_ }
            .sortedWith(compareByDescending<ConversationMember> { it.pinned_ }
                .thenByDescending { n { it.conversation_?.lastMessageAt_?.toString() } ?: "" })

        val list = members.map { member ->
            val conv  = n { member.conversation_ } ?: return@map ""
            // RG-C4 : titre = nom de l'autre participant
            val other = all(ConversationMember.tms)
                .firstOrNull { n { it.conversation_?._id_ } == conv._id_ && n { it.user_?._id_ } != uid }
            val titre = n { other?.user_?.name_ } ?: ""
            obj(
                "id"            to conv._id_.toString(),
                "titre"         to titre,
                "pinned"        to member.pinned_,
                "muted"         to member.muted_,
                "archived"      to member.archived_,
                "unread"        to member.unread_,
                "lastMessageAt" to (n { conv.lastMessageAt_?.toString() } ?: "")
            )
        }.filter { it.isNotEmpty() }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Crée une conversation entre deux utilisateurs.
 * RG-C1 : exactement 2 participants distincts.
 * RG-C2 : une seule conversation entre deux mêmes personnes.
 * RG-C3 : crée la Conversation et ses deux ConversationMember.
 */
@JsExport
@JsName("ngomaCreateConversation")
fun ngomaCreateConversation(creatorId: String, otherIdentifier: String): String {
    checkReady()?.let { return it }
    return try {
        val cid  = creatorId.toLongOrNull() ?: return error("INVALID_ID")
        val creator = all(User.tms).firstOrNull { it._id_ == cid } ?: return error("USER_NOT_FOUND")

        // RG-C3 : l'interlocuteur doit avoir un compte non supprimé
        val other = all(User.tms).firstOrNull { it.identifier_ == otherIdentifier.trim() && !it.deleted_ }
            ?: return error("USER_NOT_FOUND")

        // RG-C1 : pas de conversation avec soi-même
        if (creator._id_ == other._id_) return error("CANNOT_SELF_CONVERSE")

        // RG-C2 : si une conversation existe déjà entre les deux, on la rouvre
        // (et elle réapparaît si le créateur l'avait supprimée de son côté)
        val existing = findConversationBetween(cid, other._id_)
        if (existing != null) {
            val myMember = all(ConversationMember.tms)
                .firstOrNull { n { it.conversation_?._id_ } == existing._id_ && n { it.user_?._id_ } == cid }
            if (myMember != null && myMember.hidden_) {
                myMember.hidden_ = false
                myMember.isave()
            }
            return obj("id" to existing._id_.toString(), "existing" to true)
        }

        val now  = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val conv = Conversation().apply {
            creator_       = creator
            createdAt_     = now
            lastMessageAt_ = now
        }
        conv.isave()

        ConversationMember().apply {
            conversation_ = conv; user_ = creator
        }.isave()
        ConversationMember().apply {
            conversation_ = conv; user_ = other
        }.isave()

        obj("id" to conv._id_.toString(), "existing" to false)
    } catch (_: Throwable) { error("CREATE_FAILED") }
}

@JsExport @JsName("ngomaSetPinned")
fun ngomaSetPinned(conversationId: String, userId: String, value: Boolean): String {
    checkReady()?.let { return it }
    return patchMember(conversationId, userId) { it.pinned_ = value }
}

@JsExport @JsName("ngomaSetMuted")
fun ngomaSetMuted(conversationId: String, userId: String, value: Boolean): String {
    checkReady()?.let { return it }
    return patchMember(conversationId, userId) { it.muted_ = value }
}

@JsExport @JsName("ngomaSetArchived")
fun ngomaSetArchived(conversationId: String, userId: String, value: Boolean): String {
    checkReady()?.let { return it }
    return patchMember(conversationId, userId) { it.archived_ = value }
}

/**
 * Marque la conversation comme lue pour cet utilisateur.
 * RG-M9 : remet unread à 0 et met à jour lastReadAt.
 */
@JsExport
@JsName("ngomaMarkRead")
fun ngomaMarkRead(conversationId: String, userId: String): String {
    checkReady()?.let { return it }
    return patchMember(conversationId, userId) {
        it.unread_    = 0
        it.lastReadAt_ = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
}

// =============================================================================
// Messages
// =============================================================================

/**
 * Retourne les messages d'une conversation dans l'ordre chronologique (RG-C8).
 */
@JsExport
@JsName("ngomaMessages")
fun ngomaMessages(conversationId: String): String {
    checkReady()?.let { return it }
    return try {
        val cid  = conversationId.toLongOrNull() ?: return error("INVALID_ID")
        val list = all(Message.tms)
            .filter { n { it.conversation_?._id_ } == cid }
            .sortedBy { n { it.sentAt_?.toString() } ?: "" }
            .map { msg ->
                // Compteurs de réactions groupés par emoji
                val reacts = all(Reaction.tms)
                    .filter { n { it.message_?._id_ } == msg._id_ }
                    .groupBy { it.emoji_ }
                    .map { (emoji, list2) -> "\"${emoji}\":${list2.size}" }
                    .joinToString(",", "{", "}")

                obj(
                    "id"            to msg._id_.toString(),
                    "authorId"      to (n { msg.author_?._id_?.toString() } ?: ""),
                    "authorName"    to (n { msg.author_?.name_ } ?: ""),
                    "text"          to msg.text_,
                    "sentAt"        to (n { msg.sentAt_?.toString() } ?: ""),
                    "replyToId"     to (n { msg.replyTo_?._id_?.toString() } ?: "0"),
                    "attachment"    to msg.attachment_,
                    "edited"        to msg.edited_,
                    "deleted"       to msg.deleted_,
                    "reactions"     to reacts
                )
            }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Envoie un message.
 * RG-M2 : texte ou pièce jointe obligatoire.
 * RG-M3 : auteur doit être participant.
 * RG-M4 : au plus une pièce jointe, dont on enregistre le nom de fichier.
 * RG-M9 : met à jour lastMessageAt et incrémente unread de l'autre participant.
 */
@JsExport
@JsName("ngomaSend")
fun ngomaSend(
    conversationId: String,
    authorId: String,
    text: String,
    replyToId: String,
    attachment: String,
): String {
    checkReady()?.let { return it }
    if (text.isBlank() && attachment.isBlank()) return error("EMPTY_MESSAGE")
    return try {
        val cid  = conversationId.toLongOrNull() ?: return error("INVALID_ID")
        val aid  = authorId.toLongOrNull()        ?: return error("INVALID_ID")

        val conv   = all(Conversation.tms).firstOrNull { it._id_ == cid } ?: return error("CONV_NOT_FOUND")
        val author = all(User.tms).firstOrNull { it._id_ == aid }         ?: return error("USER_NOT_FOUND")

        // RG-M3 : vérifier que l'auteur est participant
        val members = all(ConversationMember.tms).filter { n { it.conversation_?._id_ } == cid }
        if (members.none { n { it.user_?._id_ } == aid }) return error("NOT_PARTICIPANT")

        // replyTo optionnel
        val replyMsg = if (replyToId != "0") {
            val rid = replyToId.toLongOrNull()
            if (rid != null) all(Message.tms).firstOrNull { it._id_ == rid } else null
        } else null

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val msg = Message().apply {
            conversation_ = conv
            author_       = author
            text_         = text
            attachment_   = attachment.trim()
            sentAt_       = now
        }
        if (replyMsg != null) {
            // RG-M8 : le message cité doit être de la même conversation
            if (n { replyMsg.conversation_?._id_ } != cid) return error("REPLY_WRONG_CONV")
            msg.replyTo_ = replyMsg
        }
        msg.isave()

        // RG-M9 : lastMessageAt + unread de l'autre participant
        conv.lastMessageAt_ = now
        conv.isave()
        // Si l'autre avait supprimé la conversation de son côté, elle réapparaît
        members.filter { n { it.user_?._id_ } != aid }.forEach { m ->
            m.unread_ = m.unread_ + 1
            m.hidden_ = false
            m.isave()
        }

        obj("id" to msg._id_.toString())
    } catch (_: Throwable) { error("SEND_FAILED") }
}

/**
 * Modifie un message (RG-M5, RG-M7).
 */
@JsExport
@JsName("ngomaEditMessage")
fun ngomaEditMessage(messageId: String, authorId: String, newText: String): String {
    checkReady()?.let { return it }
    return try {
        val mid = messageId.toLongOrNull() ?: return error("INVALID_ID")
        val aid = authorId.toLongOrNull()  ?: return error("INVALID_ID")
        val msg = all(Message.tms).firstOrNull { it._id_ == mid } ?: return error("NOT_FOUND")
        if (n { msg.author_?._id_ } != aid) return error("FORBIDDEN")  // RG-M5
        if (msg.deleted_)                   return error("ALREADY_DELETED") // RG-M7
        // RG-M2 : le message reste non vide (texte ou pièce jointe) ; RG-M5 : la pièce jointe n'est pas modifiable
        if (newText.isBlank() && msg.attachment_.isBlank()) return error("EMPTY_MESSAGE")
        msg.text_   = newText
        msg.edited_ = true
        msg.isave()
        obj("ok" to true)
    } catch (e: Throwable) {
        println("[NGoma] ngomaEditMessage : $e")
        obj("error" to "UPDATE_FAILED", "detail" to e.toString())
    }
}

/**
 * Suppression douce d'un message pour tout le monde (RG-M6).
 * Seul l'auteur peut supprimer.
 */
@JsExport
@JsName("ngomaDeleteMessage")
fun ngomaDeleteMessage(messageId: String, authorId: String): String {
    checkReady()?.let { return it }
    return try {
        val mid = messageId.toLongOrNull() ?: return error("INVALID_ID")
        val aid = authorId.toLongOrNull()  ?: return error("INVALID_ID")
        val msg = all(Message.tms).firstOrNull { it._id_ == mid } ?: return error("NOT_FOUND")
        if (n { msg.author_?._id_ } != aid) return error("FORBIDDEN")
        msg.text_       = ""
        msg.attachment_ = ""
        msg.deleted_    = true
        msg.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("DELETE_FAILED") }
}

/**
 * Supprime la conversation du côté de cet utilisateur seulement : hidden = true sur son
 * ConversationMember. L'autre participant la garde ; si l'autre écrit, elle réapparaît (voir ngomaSend).
 * Les messages ne sont pas effacés : RG-C2 (une seule conversation par paire) reste respectée.
 */
@JsExport
@JsName("ngomaDeleteConversation")
fun ngomaDeleteConversation(conversationId: String, userId: String): String {
    checkReady()?.let { return it }
    return patchMember(conversationId, userId) { it.hidden_ = true }
}

// =============================================================================
// Réactions
// =============================================================================

/**
 * Toggle réaction sur un message.
 * RG-R1 : emoji parmi 👍 ❤️ 😂 😮.
 * RG-R2 : au plus une réaction par user par message.
 *          Même emoji → retire. Autre emoji → remplace.
 * RG-R3 : seuls les participants peuvent réagir.
 * RG-R4 : impossible de réagir à un message supprimé.
 */
@JsExport
@JsName("ngomaReact")
fun ngomaReact(messageId: String, userId: String, emoji: String): String {
    checkReady()?.let { return it }
    val validEmojis = setOf("👍", "❤️", "😂", "😮")
    if (emoji !in validEmojis) return error("INVALID_EMOJI")  // RG-R1
    return try {
        val mid = messageId.toLongOrNull() ?: return error("INVALID_ID")
        val uid = userId.toLongOrNull()    ?: return error("INVALID_ID")
        val msg = all(Message.tms).firstOrNull { it._id_ == mid } ?: return error("NOT_FOUND")

        if (msg.deleted_) return error("MESSAGE_DELETED")  // RG-R4

        // RG-R3 : vérifier participation
        val cid = n { msg.conversation_?._id_ } ?: return error("CONV_NOT_FOUND")
        val isParticipant = all(ConversationMember.tms)
            .any { n { it.conversation_?._id_ } == cid && n { it.user_?._id_ } == uid }
        if (!isParticipant) return error("NOT_PARTICIPANT")

        val existing = all(Reaction.tms).firstOrNull {
            n { it.message_?._id_ } == mid && n { it.user_?._id_ } == uid
        }

        when {
            existing == null -> {
                // Pas encore de réaction → ajouter
                val user = all(User.tms).firstOrNull { it._id_ == uid } ?: return error("USER_NOT_FOUND")
                Reaction().apply { message_ = msg; user_ = user; emoji_ = emoji }.isave()
            }
            existing.emoji_ == emoji -> {
                // Même emoji → retirer (toggle OFF)
                existing.idelete()
            }
            else -> {
                // Autre emoji → remplacer (RG-R2)
                existing.emoji_ = emoji
                existing.isave()
            }
        }
        obj("ok" to true)
    } catch (_: Throwable) { error("REACT_FAILED") }
}

// =============================================================================
// Helpers privés
// =============================================================================

private fun findConversationBetween(uid1: Long, uid2: Long): Conversation? {
    // RG-C2 : une seule conversation possible entre deux personnes.
    val members  = all(ConversationMember.tms)
    val convIds1 = members.filter { n { it.user_?._id_ } == uid1 }
        .mapNotNull { n { it.conversation_?._id_ } }.toSet()
    val convIds2 = members.filter { n { it.user_?._id_ } == uid2 }
        .mapNotNull { n { it.conversation_?._id_ } }.toSet()
    val commonId = (convIds1 intersect convIds2).firstOrNull() ?: return null
    return all(Conversation.tms).firstOrNull { it._id_ == commonId }
}

private fun patchMember(
    conversationId: String,
    userId: String,
    f: (ConversationMember) -> Unit,
): String {
    return try {
        val cid = conversationId.toLongOrNull() ?: return error("INVALID_ID")
        val uid = userId.toLongOrNull()         ?: return error("INVALID_ID")
        val member = all(ConversationMember.tms)
            .firstOrNull { n { it.conversation_?._id_ } == cid && n { it.user_?._id_ } == uid }
            ?: return error("NOT_FOUND")
        f(member)
        member.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}
