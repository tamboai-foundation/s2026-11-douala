package ai.tambo.foundation.ngoma.models.publications

import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.tmodeler.field.common.TextField
import ai.tambo.tmodeler.field.common.IntField
import ai.tambo.tmodeler.field.advanced.BoolField
import ai.tambo.tmodeler.field.advanced.TimeField
import ai.tambo.tmodeler.field.advanced.ModelField
import ai.tambo.tmodeler.field.media.FileField
import ai.tambo.tmodeler.model.TModel
import ai.tambo.tmodeler.model.TMignore
import ai.tambo.tmodeler.ms.Tms

// ---------------------------------------------------------------------------
// Media : photo ou vidéo d'une publication (RG-F1 à RG-F3)
// ---------------------------------------------------------------------------

class Media : TModel<Media>() {

    var file      = FileField()
    var type      = TextField().default("")   // image ou video (RG-F2)
    var sizeBytes = IntField().default(0)     // vidéo ≤ 100 Mo (RG-F3)

    @TMignore var file_      by file
    @TMignore var type_      by type
    @TMignore var sizeBytes_ by sizeBytes

    companion object { val tms by lazy { Tms(Media::class) } }
}

// ---------------------------------------------------------------------------
// Post : légende et/ou média (RG-P1) ; type déduit du média
// ---------------------------------------------------------------------------

class Post : TModel<Post>() {

    var author      = ModelField(User::class)
    var caption     = TextField().default("")
    var media       = ModelField(Media::class)   // 0..1 (RG-P2)
    var edited      = BoolField().default(false) // RG-P6
    var publishedAt = TimeField()                // fil : plus récente d'abord (RG-P4)

    @TMignore var author_      by author
    @TMignore var caption_     by caption
    @TMignore var media_       by media
    @TMignore var edited_      by edited
    @TMignore var publishedAt_ by publishedAt

    companion object { val tms by lazy { Tms(Post::class) } }
}

// ---------------------------------------------------------------------------
// Like : un seul par utilisateur et par publication (RG-L1)
// ---------------------------------------------------------------------------

class Like : TModel<Like>() {

    var post      = ModelField(Post::class)
    var user      = ModelField(User::class)
    var createdAt = TimeField()

    @TMignore var post_      by post
    @TMignore var user_      by user
    @TMignore var createdAt_ by createdAt

    companion object { val tms by lazy { Tms(Like::class) } }
}

// ---------------------------------------------------------------------------
// Comment : commentaire ou réponse (parent non vide), un seul niveau (RG-K2)
// ---------------------------------------------------------------------------

class Comment : TModel<Comment>() {

    var post      = ModelField(Post::class)
    var author    = ModelField(User::class)
    var parent    = ModelField(Comment::class)   // 0..1 : vide = commentaire principal
    var text      = TextField().default("")
    var edited    = BoolField().default(false)   // RG-K3
    var deleted   = BoolField().default(false)   // suppression douce (RG-K4)
    var createdAt = TimeField()

    @TMignore var post_      by post
    @TMignore var author_    by author
    @TMignore var parent_    by parent
    @TMignore var text_      by text
    @TMignore var edited_    by edited
    @TMignore var deleted_   by deleted
    @TMignore var createdAt_ by createdAt

    companion object { val tms by lazy { Tms(Comment::class) } }
}

// ---------------------------------------------------------------------------
// CommentLike : un seul par utilisateur et par commentaire (RG-K7)
// ---------------------------------------------------------------------------

class CommentLike : TModel<CommentLike>() {

    var comment   = ModelField(Comment::class)
    var user      = ModelField(User::class)
    var createdAt = TimeField()

    @TMignore var comment_   by comment
    @TMignore var user_      by user
    @TMignore var createdAt_ by createdAt

    companion object { val tms by lazy { Tms(CommentLike::class) } }
}
