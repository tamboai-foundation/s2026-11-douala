package ai.tambo.foundation.ngoma.models.utilisateurs

import ai.tambo.tmodeler.field.common.TextField
import ai.tambo.tmodeler.field.advanced.BoolField
import ai.tambo.tmodeler.field.advanced.TimeField
import ai.tambo.tmodeler.field.advanced.ModelField
import ai.tambo.tmodeler.model.TModel
import ai.tambo.tmodeler.model.TMignore
import ai.tambo.tmodeler.ms.Tms

// ---------------------------------------------------------------------------
// User
// ---------------------------------------------------------------------------

class User : TModel<User>() {

    var name         = TextField().default("")
    var phone        = TextField().default("").unique()
    var passwordHash = TextField().default("")
    var active       = BoolField().default(true)
    var createdAt    = TimeField()

    @TMignore var name_         by name
    @TMignore var phone_        by phone
    @TMignore var passwordHash_ by passwordHash
    @TMignore var active_       by active
    @TMignore var createdAt_    by createdAt

    companion object { val tms by lazy { Tms(User::class) } }
}

// ---------------------------------------------------------------------------
// Session — utilisateur connecté (un seul enregistrement en local)
// ---------------------------------------------------------------------------

class Session : TModel<Session>() {

    var currentUser = ModelField(User::class)

    @TMignore var currentUser_ by currentUser

    companion object { val tms by lazy { Tms(Session::class) } }
}
