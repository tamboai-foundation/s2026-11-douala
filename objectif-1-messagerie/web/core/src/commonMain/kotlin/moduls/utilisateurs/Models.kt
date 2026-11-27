package ai.tambo.foundation.ngoma.models.utilisateurs

import ai.tambo.tmodeler.field.common.TextField
import ai.tambo.tmodeler.field.advanced.BoolField
import ai.tambo.tmodeler.field.advanced.TimeField
import ai.tambo.tmodeler.field.advanced.ModelField
import ai.tambo.tmodeler.model.TModel
import ai.tambo.tmodeler.model.TMignore
import ai.tambo.tmodeler.ms.Tms

// ---------------------------------------------------------------------------
// User : RG-U1 à RG-U12
// ---------------------------------------------------------------------------

class User : TModel<User>() {

    var name         = TextField().default("")
    var identifier   = TextField().default("").unique()   // téléphone ou e-mail (RG-U2)
    var passwordHash = TextField().default("")            // empreinte du mot de passe (RG-U4)
    var bio          = TextField().default("")            // description du profil (RG-U9)
    var joinedAt     = TimeField()                        // « Membre depuis le … » (RG-U7)
    var deleted      = BoolField().default(false)         // compte supprimé (RG-U12)

    @TMignore var name_         by name
    @TMignore var identifier_   by identifier
    @TMignore var passwordHash_ by passwordHash
    @TMignore var bio_          by bio
    @TMignore var joinedAt_     by joinedAt
    @TMignore var deleted_      by deleted

    companion object { val tms by lazy { Tms(User::class) } }
}

// ---------------------------------------------------------------------------
// Session : utilisateur connecté sur ce navigateur (RG-U6)
// ---------------------------------------------------------------------------

class Session : TModel<Session>() {

    var currentUser = ModelField(User::class)   // 0..1

    @TMignore var currentUser_ by currentUser

    companion object { val tms by lazy { Tms(Session::class) } }
}
