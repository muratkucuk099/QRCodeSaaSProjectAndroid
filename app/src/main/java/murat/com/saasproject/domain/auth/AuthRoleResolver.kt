package murat.com.saasproject.domain.auth

import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.domain.config.MainAdminConfig

/**
 * iOS `FirebaseService.checkRole` ile aynı sıra:
 *   1. UID == main-admin sabiti
 *   2. `users/{uid}` varsa müşteri
 *   3. `businesses/{uid}` varsa işletme
 *   4. hiçbiri → yetkisiz
 *
 * Yeni collection / `role` alanı yok.
 */
object AuthRoleResolver {

    fun resolve(
        uid: String,
        userDocumentExists: Boolean,
        businessDocumentExists: Boolean
    ): LoginResult? {
        if (uid == MainAdminConfig.UID) return LoginResult.MAIN_ADMIN
        if (userDocumentExists) return LoginResult.USER
        if (businessDocumentExists) return LoginResult.SUB_ADMIN
        return null
    }
}
