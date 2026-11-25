package ai.tambo.foundation.ngoma.models.publications

import ai.tambo.foundation.ngoma.models.conversations.Media
import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.tmodeler.field.common.TextField
import ai.tambo.tmodeler.field.common.IntField
import ai.tambo.tmodeler.field.advanced.BoolField
import ai.tambo.tmodeler.field.advanced.TimeField
import ai.tambo.tmodeler.field.advanced.ModelField
import ai.tambo.tmodeler.model.TModel
import ai.tambo.tmodeler.model.TMignore
import ai.tambo.tmodeler.ms.Tms

// ---------------------------------------------------------------------------
// Post — publication
// ---------------------------------------------------------------------------

class Post : TModel<Post>() {

    var author        = ModelField(User::class)
    var type          = TextField().default("legende")  // image, audio, video, legende
    var caption       = TextField().default("")
    var media         = ModelField(Media::class)        // 0..1 : absent si type = legende
    var likesCount    = IntField().default(0)
    var commentsCount = IntField().default(0)
    var edited        = BoolField().default(false)
    var publishedAt   = TimeField()

    @TMignore var author_        by author
    @TMignore var type_          by type
    @TMignore var caption_       by caption
    @TMignore var media_         by media
    @TMignore var likesCount_    by likesCount
    @TMignore var commentsCount_ by commentsCount
    @TMignore var edited_        by edited
    @TMignore var publishedAt_   by publishedAt

    companion object { val tms by lazy { Tms(Post::class) } }
}

// ---------------------------------------------------------------------------
// Like
// ---------------------------------------------------------------------------

class Like : TModel<Like>() {

    var post      = ModelField(Post::class)
    var user      = ModelField(User::class)
    var emoji     = TextField().default("")   // 👍 ❤️ 😂 😮
    var createdAt = TimeField()

    @TMignore var post_      by post
    @TMignore var user_      by user
    @TMignore var emoji_     by emoji
    @TMignore var createdAt_ by createdAt

    companion object { val tms by lazy { Tms(Like::class) } }
}

// ---------------------------------------------------------------------------
// Comment
// ---------------------------------------------------------------------------

class Comment : TModel<Comment>() {

    var post      = ModelField(Post::class)
    var author    = ModelField(User::class)
    var text      = TextField().default("")
    var createdAt = TimeField()

    @TMignore var post_      by post
    @TMignore var author_    by author
    @TMignore var text_      by text
    @TMignore var createdAt_ by createdAt

    companion object { val tms by lazy { Tms(Comment::class) } }
}

// ---------------------------------------------------------------------------
// Reply — réponse à un commentaire (un seul niveau, RG-K2)
// ---------------------------------------------------------------------------

class Reply : TModel<Reply>() {

    var comment   = ModelField(Comment::class)
    var author    = ModelField(User::class)
    var text      = TextField().default("")
    var createdAt = TimeField()

    @TMignore var comment_   by comment
    @TMignore var author_    by author
    @TMignore var text_      by text
    @TMignore var createdAt_ by createdAt

    companion object { val tms by lazy { Tms(Reply::class) } }
}
