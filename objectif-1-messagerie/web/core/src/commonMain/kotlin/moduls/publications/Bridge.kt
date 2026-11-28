@file:OptIn(ExperimentalJsExport::class)

package ai.tambo.foundation.ngoma.moduls.publications

import ai.tambo.foundation.ngoma.NGomaCore
import ai.tambo.foundation.ngoma.models.publications.Comment
import ai.tambo.foundation.ngoma.models.publications.Like
import ai.tambo.foundation.ngoma.models.publications.Post
import ai.tambo.foundation.ngoma.models.publications.Reply
import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.foundation.ngoma.moduls.utilisateurs.all
import ai.tambo.foundation.ngoma.moduls.utilisateurs.arr
import ai.tambo.foundation.ngoma.moduls.utilisateurs.error
import ai.tambo.foundation.ngoma.moduls.utilisateurs.n
import ai.tambo.foundation.ngoma.moduls.utilisateurs.obj
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

private val VALID_TYPES  = setOf("image", "audio", "video", "legende")
private val VALID_EMOJIS = setOf("👍", "❤️", "😂", "😮")
private val MAX_VIDEO_BYTES = 100L * 1024 * 1024   // RG-F3 : 100 Mo

private fun checkReady(): String? = if (!NGomaCore.ready_) error("NOT_READY") else null

// =============================================================================
// Publications
// =============================================================================

/**
 * Retourne toutes les publications (RG-P4 : tout utilisateur connecté peut consulter).
 * Triées par publishedAt décroissant.
 */
@JsExport
@JsName("ngomaPosts")
fun ngomaPosts(): String {
    checkReady()?.let { return it }
    return try {
        val list = all(Post.tms)
            .sortedByDescending { n { it.publishedAt_?.toString() } ?: "" }
            .map { postJson(it) }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Retourne les publications d'un utilisateur.
 */
@JsExport
@JsName("ngomaUserPosts")
fun ngomaUserPosts(authorId: String): String {
    checkReady()?.let { return it }
    return try {
        val aid  = authorId.toLongOrNull() ?: return error("INVALID_ID")
        val list = all(Post.tms)
            .filter { n { it.author_?._id_ } == aid }
            .sortedByDescending { n { it.publishedAt_?.toString() } ?: "" }
            .map { postJson(it) }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Crée une publication.
 * RG-P1 : auteur et type obligatoires.
 * RG-P2 : type legende → légende non vide, pas de média.
 * RG-P3 : type image/audio/video → média obligatoire du même type.
 */
@JsExport
@JsName("ngomaCreatePost")
fun ngomaCreatePost(authorId: String, type: String, caption: String): String {
    checkReady()?.let { return it }
    if (type !in VALID_TYPES) return error("INVALID_TYPE")  // RG-P1
    if (type == "legende" && caption.isBlank()) return error("CAPTION_REQUIRED")  // RG-P2
    return try {
        val aid    = authorId.toLongOrNull() ?: return error("INVALID_ID")
        val author = all(User.tms).firstOrNull { it._id_ == aid } ?: return error("USER_NOT_FOUND")
        val now    = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val post   = Post().apply {
            author_      = author
            type_        = type
            caption_     = caption
            publishedAt_ = now
        }
        post.isave()
        obj("id" to post._id_.toString())
    } catch (_: Throwable) { error("CREATE_FAILED") }
}

/**
 * Modifie une publication (RG-P5).
 */
@JsExport
@JsName("ngomaEditPost")
fun ngomaEditPost(postId: String, authorId: String, newCaption: String): String {
    checkReady()?.let { return it }
    return try {
        val pid = postId.toLongOrNull()   ?: return error("INVALID_ID")
        val aid = authorId.toLongOrNull() ?: return error("INVALID_ID")
        val post = all(Post.tms).firstOrNull { it._id_ == pid } ?: return error("NOT_FOUND")
        if (n { post.author_?._id_ } != aid) return error("FORBIDDEN")  // RG-P5
        if (post.type_ == "legende" && newCaption.isBlank()) return error("CAPTION_REQUIRED")
        post.caption_ = newCaption
        post.edited_  = true
        post.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}

/**
 * Supprime une publication et tout ce qui y est rattaché (RG-P6).
 */
@JsExport
@JsName("ngomaDeletePost")
fun ngomaDeletePost(postId: String, authorId: String): String {
    checkReady()?.let { return it }
    return try {
        val pid = postId.toLongOrNull()   ?: return error("INVALID_ID")
        val aid = authorId.toLongOrNull() ?: return error("INVALID_ID")
        val post = all(Post.tms).firstOrNull { it._id_ == pid } ?: return error("NOT_FOUND")
        if (n { post.author_?._id_ } != aid) return error("FORBIDDEN")

        // RG-P6 : supprimer likes, commentaires et leurs réponses
        all(Like.tms).filter { n { it.post_?._id_ } == pid }.forEach { it.idelete() }
        val comments = all(Comment.tms).filter { n { it.post_?._id_ } == pid }
        comments.forEach { c ->
            all(Reply.tms).filter { n { it.comment_?._id_ } == c._id_ }.forEach { it.idelete() }
            c.idelete()
        }
        post.idelete()
        obj("ok" to true)
    } catch (_: Throwable) { error("DELETE_FAILED") }
}

// =============================================================================
// Likes
// =============================================================================

/**
 * Toggle like sur une publication.
 * RG-L1 : emoji parmi 👍 ❤️ 😂 😮.
 * RG-L2 : un user peut laisser au plus 1 like par emoji sur une même publication.
 * RG-L3 : cliquer à nouveau retire le like.
 * RG-L4 : likesCount mis à jour.
 */
@JsExport
@JsName("ngomaLikePost")
fun ngomaLikePost(postId: String, userId: String, emoji: String): String {
    checkReady()?.let { return it }
    if (emoji !in VALID_EMOJIS) return error("INVALID_EMOJI")
    return try {
        val pid  = postId.toLongOrNull()  ?: return error("INVALID_ID")
        val uid  = userId.toLongOrNull()  ?: return error("INVALID_ID")
        val post = all(Post.tms).firstOrNull { it._id_ == pid } ?: return error("NOT_FOUND")
        val user = all(User.tms).firstOrNull { it._id_ == uid } ?: return error("USER_NOT_FOUND")

        val existing = all(Like.tms).firstOrNull {
            n { it.post_?._id_ } == pid &&
            n { it.user_?._id_ } == uid &&
            it.emoji_ == emoji
        }

        if (existing != null) {
            // RG-L3 : toggle OFF → retirer
            existing.idelete()
            post.likesCount_ = maxOf(0, post.likesCount_ - 1)   // RG-L4 : jamais sous 0
        } else {
            // Toggle ON → ajouter
            Like().apply {
                post_      = post
                user_      = user
                this.emoji_ = emoji
                createdAt_ = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }.isave()
            post.likesCount_ = post.likesCount_ + 1              // RG-L4
        }
        post.isave()
        obj("ok" to true, "likesCount" to post.likesCount_)
    } catch (_: Throwable) { error("LIKE_FAILED") }
}

// =============================================================================
// Commentaires
// =============================================================================

/**
 * Retourne les commentaires d'une publication avec leurs réponses.
 * RG-K5 : commentaires du plus récent au plus ancien ;
 *          réponses dans l'ordre chronologique.
 */
@JsExport
@JsName("ngomaComments")
fun ngomaComments(postId: String): String {
    checkReady()?.let { return it }
    return try {
        val pid      = postId.toLongOrNull() ?: return error("INVALID_ID")
        val comments = all(Comment.tms)
            .filter { n { it.post_?._id_ } == pid }
            .sortedByDescending { n { it.createdAt_?.toString() } ?: "" }  // RG-K5

        val list = comments.map { c ->
            val replies = all(Reply.tms)
                .filter { n { it.comment_?._id_ } == c._id_ }
                .sortedBy { n { it.createdAt_?.toString() } ?: "" }         // RG-K5
                .map { r ->
                    obj(
                        "id"        to r._id_.toString(),
                        "authorId"  to (n { r.author_?._id_?.toString() } ?: ""),
                        "authorName" to (n { r.author_?.name_ } ?: ""),
                        "text"      to r.text_,
                        "createdAt" to (n { r.createdAt_?.toString() } ?: "")
                    )
                }
            val repliesJson = replies.joinToString(",", "[", "]")
            "{\"id\":\"${c._id_}\"," +
            "\"authorId\":\"${n { c.author_?._id_ } ?: ""}\"," +
            "\"authorName\":\"${n { c.author_?.name_ } ?: ""}\"," +
            "\"text\":\"${c.text_.replace("\"", "\\\"")}\"," +
            "\"createdAt\":\"${n { c.createdAt_?.toString() } ?: ""}\"," +
            "\"replies\":$repliesJson}"
        }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Ajoute un commentaire (RG-K1, RG-K3).
 */
@JsExport
@JsName("ngomaAddComment")
fun ngomaAddComment(postId: String, authorId: String, text: String): String {
    checkReady()?.let { return it }
    if (text.isBlank()) return error("EMPTY_COMMENT")  // RG-K1
    return try {
        val pid    = postId.toLongOrNull()   ?: return error("INVALID_ID")
        val aid    = authorId.toLongOrNull() ?: return error("INVALID_ID")
        val post   = all(Post.tms).firstOrNull { it._id_ == pid } ?: return error("NOT_FOUND")
        val author = all(User.tms).firstOrNull { it._id_ == aid } ?: return error("USER_NOT_FOUND")

        Comment().apply {
            post_      = post
            author_    = author
            text_      = text
            createdAt_ = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }.isave()

        // RG-K3 : incrémenter commentsCount
        post.commentsCount_ = post.commentsCount_ + 1
        post.isave()
        obj("ok" to true, "commentsCount" to post.commentsCount_)
    } catch (_: Throwable) { error("COMMENT_FAILED") }
}

/**
 * Ajoute une réponse à un commentaire (RG-K1, RG-K2).
 */
@JsExport
@JsName("ngomaAddReply")
fun ngomaAddReply(commentId: String, authorId: String, text: String): String {
    checkReady()?.let { return it }
    if (text.isBlank()) return error("EMPTY_REPLY")  // RG-K1
    return try {
        val cid     = commentId.toLongOrNull() ?: return error("INVALID_ID")
        val aid     = authorId.toLongOrNull()  ?: return error("INVALID_ID")
        val comment = all(Comment.tms).firstOrNull { it._id_ == cid } ?: return error("NOT_FOUND")
        val author  = all(User.tms).firstOrNull { it._id_ == aid }    ?: return error("USER_NOT_FOUND")

        Reply().apply {
            comment_   = comment        // RG-K2 : pointe vers Comment, jamais Reply
            author_    = author
            text_      = text
            createdAt_ = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("REPLY_FAILED") }
}

// ============================================================================
// Helpers privés
// ============================================================================

private fun postJson(post: Post): String = obj(
    "id"            to post._id_.toString(),
    "authorId"      to (n { post.author_?._id_?.toString() } ?: ""),
    "authorName"    to (n { post.author_?.name_ } ?: ""),
    "type"          to post.type_,
    "caption"       to post.caption_,
    "likesCount"    to post.likesCount_,
    "commentsCount" to post.commentsCount_,
    "edited"        to post.edited_,
    "publishedAt"   to (n { post.publishedAt_?.toString() } ?: "")
)
