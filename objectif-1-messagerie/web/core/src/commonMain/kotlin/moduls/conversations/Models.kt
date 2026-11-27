package ai.tambo.foundation.ngoma.models.conversations

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
// Conversation : titre non stocké, c'est le nom de l'autre participant (RG-C4)
// ---------------------------------------------------------------------------

class Conversation : TModel<Conversation>() {

    var creator       = ModelField(User::class)
    var createdAt     = TimeField()
    var lastMessageAt = TimeField()   // tri de la liste (RG-C7)

    @TMignore var creator_       by creator
    @TMignore var createdAt_     by createdAt
    @TMignore var lastMessageAt_ by lastMessageAt

    companion object { val tms by lazy { Tms(Conversation::class) } }
}

// ---------------------------------------------------------------------------
// ConversationMember : exactement 2 par conversation (RG-C1),
// réglages personnels de chaque participant (RG-C5)
// ---------------------------------------------------------------------------

class ConversationMember : TModel<ConversationMember>() {

    var conversation = ModelField(Conversation::class)
    var user         = ModelField(User::class)
    var pinned       = BoolField().default(false)
    var muted        = BoolField().default(false)
    var archived     = BoolField().default(false)
    var unread       = IntField().default(0)       // RG-M9
    var lastReadAt   = TimeField()
    // hidden = true : ce participant a supprimé la conversation de son côté.
    // L'autre participant n'est pas touché ; si l'autre écrit, hidden repasse à false.
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
// Message : RG-M1 à RG-M10
// ---------------------------------------------------------------------------

class Message : TModel<Message>() {

    var conversation = ModelField(Conversation::class)
    var author       = ModelField(User::class)
    var text         = TextField().default("")
    var replyTo      = ModelField(Message::class)   // 0..1, même conversation (RG-M8)
    var attachment   = TextField().default("")      // nom du fichier joint (RG-M4)
    var edited       = BoolField().default(false)
    var deleted      = BoolField().default(false)   // suppression douce (RG-M6)
    var sentAt       = TimeField()

    @TMignore var conversation_ by conversation
    @TMignore var author_       by author
    @TMignore var text_         by text
    @TMignore var replyTo_      by replyTo
    @TMignore var attachment_   by attachment
    @TMignore var edited_       by edited
    @TMignore var deleted_      by deleted
    @TMignore var sentAt_       by sentAt

    companion object { val tms by lazy { Tms(Message::class) } }
}

// ---------------------------------------------------------------------------
// Reaction : au plus une par utilisateur et par message (RG-R2)
// ---------------------------------------------------------------------------

class Reaction : TModel<Reaction>() {

    var message = ModelField(Message::class)
    var user    = ModelField(User::class)
    var emoji   = TextField().default("")   // 👍 ❤️ 😂 😮 (RG-R1)

    @TMignore var message_ by message
    @TMignore var user_    by user
    @TMignore var emoji_   by emoji

    companion object { val tms by lazy { Tms(Reaction::class) } }
}
