@file:OptIn(ExperimentalJsExport::class)

package ai.tambo.foundation.ngoma.moduls.publications

import ai.tambo.foundation.ngoma.NGomaCore
import ai.tambo.foundation.ngoma.models.publications.Comment
import ai.tambo.foundation.ngoma.models.publications.CommentLike
import ai.tambo.foundation.ngoma.models.publications.Like
import ai.tambo.foundation.ngoma.models.publications.Post
import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.foundation.ngoma.moduls.utilisateurs.all
import ai.tambo.foundation.ngoma.moduls.utilisateurs.arr
import ai.tambo.foundation.ngoma.moduls.utilisateurs.error
import ai.tambo.foundation.ngoma.moduls.utilisateurs.n
import ai.tambo.foundation.ngoma.moduls.utilisateurs.now
import ai.tambo.foundation.ngoma.moduls.utilisateurs.obj
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

private fun checkReady(): String? = if (!NGomaCore.ready_) error("NOT_READY") else null

private fun findPost(postId: String): Post? {
    val pid = postId.toLongOrNull() ?: return null
    return all(Post.tms).firstOrNull { it._id_ == pid }
}

private fun findComment(commentId: String): Comment? {
    val cid = commentId.toLongOrNull() ?: return null
    return all(Comment.tms).firstOrNull { it._id_ == cid }
}

private fun findActiveUser(userId: String): User? {
    val uid = userId.toLongOrNull() ?: return null
    return all(User.tms).firstOrNull { it._id_ == uid && !it.deleted_ }
}

// =============================================================================
// Publications
// =============================================================================

/** Fil : publications de tous les utilisateurs, la plus récente en premier (RG-P4). */
@JsExport
@JsName("ngomaPosts")
fun ngomaPosts(): String {
    checkReady()?.let { return it }
    return try {
        arr(all(Post.tms).sortedByDescending { n { it.publishedAt_?.toString() } ?: "" }.map { postJson(it) })
    } catch (_: Throwable) { error("READ_FAILED") }
}

/** Publications d'un utilisateur, pour son profil (RG-P5). */
@JsExport
@JsName("ngomaUserPosts")
fun ngomaUserPosts(authorId: String): String {
    checkReady()?.let { return it }
    return try {
        val aid = authorId.toLongOrNull() ?: return error("INVALID_ID")
        arr(all(Post.tms)
            .filter { n { it.author_?._id_ } == aid }
            .sortedByDescending { n { it.publishedAt_?.toString() } ?: "" }
            .map { postJson(it) })
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Publie une légende seule.
 * RG-P1 : au moins une légende ou un média. L'envoi du fichier (photo ou vidéo) n'est pas encore
 * branché : sans média, la légende est donc obligatoire.
 */
@JsExport
@JsName("ngomaCreatePost")
fun ngomaCreatePost(authorId: String, caption: String): String {
    checkReady()?.let { return it }
    if (caption.isBlank()) return error("CAPTION_REQUIRED")
    return try {
        val author = findActiveUser(authorId) ?: return error("USER_NOT_FOUND")
        val post = Post().apply {
            author_      = author
            caption_     = caption.trim()
            publishedAt_ = now()
        }
        post.isave()
        postJson(post)
    } catch (_: Throwable) { error("CREATE_FAILED") }
}

/**
 * Modifie la légende (RG-P6) : seul l'auteur ; une publication sans média garde une légende non vide.
 */
@JsExport
@JsName("ngomaEditPost")
fun ngomaEditPost(postId: String, authorId: String, newCaption: String): String {
    checkReady()?.let { return it }
    return try {
        val post = findPost(postId) ?: return error("NOT_FOUND")
        if (n { post.author_?._id_ }?.toString() != authorId) return error("FORBIDDEN")
        val hasMedia = n { post.media_ } != null
        if (!hasMedia && newCaption.isBlank()) return error("CAPTION_REQUIRED")
        post.caption_ = newCaption.trim()
        post.edited_  = true
        post.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}

/** Supprime une publication et tout ce qui y est rattaché (RG-P7) ; seul l'auteur. */
@JsExport
@JsName("ngomaDeletePost")
fun ngomaDeletePost(postId: String, authorId: String): String {
    checkReady()?.let { return it }
    return try {
        val post = findPost(postId) ?: return error("NOT_FOUND")
        if (n { post.author_?._id_ }?.toString() != authorId) return error("FORBIDDEN")
        deletePostCascade(post)
        obj("ok" to true)
    } catch (_: Throwable) { error("DELETE_FAILED") }
}

/** RG-P7 : likes, commentaires et réponses, likes de ces commentaires, média, puis la publication. */
internal fun deletePostCascade(post: Post) {
    val pid = post._id_
    all(Like.tms).filter { n { it.post_?._id_ } == pid }.forEach { it.idelete() }
    val comments = all(Comment.tms).filter { n { it.post_?._id_ } == pid }
    val commentIds = comments.map { it._id_ }.toSet()
    all(CommentLike.tms).filter { n { it.comment_?._id_ } in commentIds }.forEach { it.idelete() }
    comments.forEach { it.idelete() }
    n { post.media_ }?.idelete()
    post.idelete()
}

// =============================================================================
// Likes sur les publications
// =============================================================================

/** « J'aime » en bascule : un seul par utilisateur et par publication (RG-L1, RG-L2). */
@JsExport
@JsName("ngomaToggleLike")
fun ngomaToggleLike(postId: String, userId: String): String {
    checkReady()?.let { return it }
    return try {
        val post = findPost(postId)       ?: return error("NOT_FOUND")
        val user = findActiveUser(userId) ?: return error("USER_NOT_FOUND")
        val existing = all(Like.tms).firstOrNull {
            n { it.post_?._id_ } == post._id_ && n { it.user_?._id_ } == user._id_
        }
        if (existing != null) existing.idelete()
        else Like().apply { post_ = post; user_ = user; createdAt_ = now() }.isave()
        obj("ok" to true, "liked" to (existing == null))
    } catch (_: Throwable) { error("LIKE_FAILED") }
}

// =============================================================================
// Commentaires et réponses
// =============================================================================

/**
 * Commentaires d'une publication (réponses comprises, avec leur parentId),
 * du plus ancien au plus récent (RG-K6).
 */
@JsExport
@JsName("ngomaComments")
fun ngomaComments(postId: String): String {
    checkReady()?.let { return it }
    return try {
        val pid = postId.toLongOrNull() ?: return error("INVALID_ID")
        val likes = all(CommentLike.tms)
        val list = all(Comment.tms)
            .filter { n { it.post_?._id_ } == pid }
            .sortedBy { n { it.createdAt_?.toString() } ?: "" }
            .map { c -> commentJson(c, likes.filter { n { it.comment_?._id_ } == c._id_ }) }
        arr(list)
    } catch (_: Throwable) { error("READ_FAILED") }
}

/**
 * Ajoute un commentaire, ou une réponse si parentId ≠ "0".
 * RG-K1 : non vide. RG-K2 : répondre à une réponse rattache au commentaire principal.
 * RG-K8 : pas de réponse à un commentaire supprimé.
 */
@JsExport
@JsName("ngomaAddComment")
fun ngomaAddComment(postId: String, authorId: String, text: String, parentId: String): String {
    checkReady()?.let { return it }
    if (text.isBlank()) return error("EMPTY_COMMENT")
    return try {
        val post   = findPost(postId)         ?: return error("NOT_FOUND")
        val author = findActiveUser(authorId) ?: return error("USER_NOT_FOUND")
        var parent: Comment? = null
        if (parentId != "0" && parentId.isNotBlank()) {
            val target = findComment(parentId) ?: return error("NOT_FOUND")
            if (n { target.post_?._id_ } != post._id_) return error("WRONG_POST")
            parent = n { target.parent_ } ?: target          // RG-K2 : un seul niveau
            if (parent.deleted_) return error("COMMENT_DELETED") // RG-K8
        }
        val comment = Comment().apply {
            post_      = post
            author_    = author
            text_      = text.trim()
            createdAt_ = now()
        }
        if (parent != null) comment.parent_ = parent
        comment.isave()
        obj("ok" to true, "id" to comment._id_.toString())
    } catch (_: Throwable) { error("COMMENT_FAILED") }
}

/** Modifie son commentaire (RG-K3, RG-K8). */
@JsExport
@JsName("ngomaEditComment")
fun ngomaEditComment(commentId: String, authorId: String, text: String): String {
    checkReady()?.let { return it }
    if (text.isBlank()) return error("EMPTY_COMMENT")
    return try {
        val c = findComment(commentId) ?: return error("NOT_FOUND")
        if (n { c.author_?._id_ }?.toString() != authorId) return error("FORBIDDEN")
        if (c.deleted_) return error("COMMENT_DELETED")
        c.text_   = text.trim()
        c.edited_ = true
        c.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("UPDATE_FAILED") }
}

/** Supprime son commentaire : suppression douce (RG-K4). */
@JsExport
@JsName("ngomaRemoveComment")
fun ngomaRemoveComment(commentId: String, authorId: String): String {
    checkReady()?.let { return it }
    return try {
        val c = findComment(commentId) ?: return error("NOT_FOUND")
        if (n { c.author_?._id_ }?.toString() != authorId) return error("FORBIDDEN")
        c.text_    = ""
        c.deleted_ = true
        c.isave()
        obj("ok" to true)
    } catch (_: Throwable) { error("DELETE_FAILED") }
}

/** « J'aime » en bascule sur un commentaire (RG-K7, RG-K8). */
@JsExport
@JsName("ngomaToggleCommentLike")
fun ngomaToggleCommentLike(commentId: String, userId: String): String {
    checkReady()?.let { return it }
    return try {
        val c    = findComment(commentId)  ?: return error("NOT_FOUND")
        val user = findActiveUser(userId)  ?: return error("USER_NOT_FOUND")
        if (c.deleted_) return error("COMMENT_DELETED")
        val existing = all(CommentLike.tms).firstOrNull {
            n { it.comment_?._id_ } == c._id_ && n { it.user_?._id_ } == user._id_
        }
        if (existing != null) existing.idelete()
        else CommentLike().apply { comment_ = c; user_ = user; createdAt_ = now() }.isave()
        obj("ok" to true, "liked" to (existing == null))
    } catch (_: Throwable) { error("LIKE_FAILED") }
}

// =============================================================================
// Helpers privés : JSON au format attendu par l'interface Foundation (Post, PostComment)
// =============================================================================

private fun idList(ids: List<Long>): String = ids.joinToString(",", "[", "]")

private fun postJson(post: Post): String {
    val likers = all(Like.tms).filter { n { it.post_?._id_ } == post._id_ }.mapNotNull { n { it.user_?._id_ } }
    val media  = n { post.media_ }
    val base = obj(
        "id"          to post._id_.toString(),
        "authorId"    to (n { post.author_?._id_?.toString() } ?: ""),
        "caption"     to post.caption_,
        "mediaType"   to (media?.type_ ?: ""),
        "edited"      to post.edited_,
        "publishedAt" to (n { post.publishedAt_?.toString() } ?: ""),
    )
    return base.dropLast(1) + ",\"likes\":${idList(likers)}}"
}

private fun commentJson(c: Comment, likes: List<CommentLike>): String {
    val base = obj(
        "id"        to c._id_.toString(),
        "authorId"  to (n { c.author_?._id_?.toString() } ?: ""),
        "parentId"  to (n { c.parent_?._id_?.toString() } ?: ""),
        "text"      to c.text_,
        "edited"    to c.edited_,
        "deleted"   to c.deleted_,
        "createdAt" to (n { c.createdAt_?.toString() } ?: ""),
    )
    return base.dropLast(1) + ",\"likes\":${idList(likes.mapNotNull { n { it.user_?._id_ } })}}"
}
