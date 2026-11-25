package ai.tambo.foundation.ngoma.models.conversations

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
// Media — entité partagée (messagerie + publications)
// ---------------------------------------------------------------------------

class Media : TModel<Media>() {

    var file      = FileField()
    var type      = TextField().default("")   // image, audio, video
    var sizeBytes = IntField().default(0)

    @TMignore var file_      by file
    @TMignore var type_      by type
    @TMignore var sizeBytes_ by sizeBytes

    companion object { val tms by lazy { Tms(Media::class) } }
}

// ---------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------

class Conversation : TModel<Conversation>() {

    var creator       = ModelField(User::class)
    var createdAt     = TimeField()
    var lastMessageAt = TimeField()

    @TMignore var creator_       by creator
    @TMignore var createdAt_     by createdAt
    @TMignore var lastMessageAt_ by lastMessageAt

    companion object { val tms by lazy { Tms(Conversation::class) } }
}

// ---------------------------------------------------------------------------
// ConversationMember — réglages personnels de chaque participant
// ---------------------------------------------------------------------------

class ConversationMember : TModel<ConversationMember>() {

    var conversation = ModelField(Conversation::class)
    var user         = ModelField(User::class)
    var pinned       = BoolField().default(false)
    var muted        = BoolField().default(false)
    var archived     = BoolField().default(false)
    var unread       = IntField().default(0)
    var lastReadAt   = TimeField()
    // hidden = true : l'utilisateur a "supprimé" la conversation de son côté.
    // Le membre reste en base pour que l'autre participant puisse continuer à écrire.
    // Si l'autre envoie un message → hidden repasse à false automatiquement.
    var hidden       = BoolField().default(false)

    @TMignore var conversation_ by conversation
    @TMignore var user_         by user
    @TMignore var pinned_       by pinned
    @TMignore var muted_        by muted
    @TMignore var archived_     by archived
    @TMignore var unread_       by unread
    @TMignore var lastReadAt_   by lastReadAt
    @TMignore var hidden_       by hidden

    companion object { val tms by lazy { Tms(ConversationMember::class) } }
}

// ---------------------------------------------------------------------------
// Message
// ---------------------------------------------------------------------------

class Message : TModel<Message>() {

    var conversation = ModelField(Conversation::class)
    var author       = ModelField(User::class)
    var text         = TextField().default("")
    var replyTo      = ModelField(Message::class)       // auto-référence (0..1)
    var attachment   = ModelField(Media::class)         // pièce jointe optionnelle (0..1)
    var edited       = BoolField().default(false)
    var deleted      = BoolField().default(false)
    var sentAt       = TimeField()
    var deletedForIds = TextField().default("")   // ids séparés par virgule, ex: "7,12"

    @TMignore var conversation_ by conversation
    @TMignore var author_       by author
    @TMignore var text_         by text
    @TMignore var replyTo_      by replyTo
    @TMignore var attachment_   by attachment
    @TMignore var edited_       by edited
    @TMignore var deleted_      by deleted
    @TMignore var sentAt_       by sentAt
    @TMignore var deletedForIds_ by deletedForIds

    companion object { val tms by lazy { Tms(Message::class) } }
}

// ---------------------------------------------------------------------------
// Reaction — une ligne par réaction (RG-R2 : au plus une par user par message)
// ---------------------------------------------------------------------------

class Reaction : TModel<Reaction>() {

    var message = ModelField(Message::class)
    var user    = ModelField(User::class)
    var emoji   = TextField().default("")   // 👍 ❤️ 😂 😮

    @TMignore var message_ by message
    @TMignore var user_    by user
    @TMignore var emoji_   by emoji

    companion object { val tms by lazy { Tms(Reaction::class) } }
}
