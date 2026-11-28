@file:OptIn(ExperimentalJsExport::class)

package ai.tambo.foundation.ngoma

import ai.tambo.foundation.ngoma.generated.registerAllTModels
import ai.tambo.foundation.ngoma.models.conversations.Conversation
import ai.tambo.foundation.ngoma.models.conversations.ConversationMember
import ai.tambo.foundation.ngoma.models.conversations.Message
import ai.tambo.foundation.ngoma.models.conversations.Reaction
import ai.tambo.foundation.ngoma.models.publications.Comment
import ai.tambo.foundation.ngoma.models.publications.CommentLike
import ai.tambo.foundation.ngoma.models.publications.Like
import ai.tambo.foundation.ngoma.models.publications.Media
import ai.tambo.foundation.ngoma.models.publications.Post
import ai.tambo.foundation.ngoma.models.utilisateurs.Session
import ai.tambo.foundation.ngoma.models.utilisateurs.User
import ai.tambo.tmodeler.tmodelerInit
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

// =============================================================================
// SOLID
// S — NGomaTableInitializer : crée les tables. NGomaCore : orchestre le démarrage.
// O — NGomaCore.init() accepte un ITableInitializer injectable.
// I — ITableInitializer : une seule méthode.
// D — NGomaCore dépend de l'abstraction, pas de la classe concrète.
// =============================================================================

interface ITableInitializer {
    fun commitAll()
}

/**
 * Ordre des commit : d'abord les entités sans dépendance, puis celles qui en ont.
 * RG-T5 : DB_VERSION incrémenté → repart d'une base vide.
 */
object NGomaTableInitializer : ITableInitializer {
    override fun commitAll() {
        // Module utilisateurs
        User.tms.commit()
        Session.tms.commit()
        // Module conversations
        Conversation.tms.commit()
        ConversationMember.tms.commit()
        Message.tms.commit()
        Reaction.tms.commit()
        // Module publications (Media avant Post, Comment avant CommentLike)
        Media.tms.commit()
        Post.tms.commit()
        Like.tms.commit()
        Comment.tms.commit()
        CommentLike.tms.commit()
    }
}

object NGomaCore {

    // RG-T5 : incrémenter après toute modification de structure de modèle.
    const val DB_VERSION = 7

    var ready_ = false
        private set

    fun init(
        dbName: String,
        tableInit: ITableInitializer = NGomaTableInitializer,
    ): Boolean {
        if (ready_) return true
        return try {
            registerAllTModels()
            if (!tmodelerInit("$dbName-$DB_VERSION")) return false
            tableInit.commitAll()
            ready_ = true
            true
        } catch (_: Throwable) {
            false
        }
    }
}

@JsExport
@JsName("ngomaInit")
fun ngomaInit(dbName: String): Boolean = try {
    NGomaCore.init(dbName)
} catch (_: Throwable) {
    false
}
