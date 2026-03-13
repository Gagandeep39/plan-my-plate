package com.planmyplate.util

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.planmyplate.data.repository.UserRepository

object AuthAccountResolver {

    fun resolveGoogleAccount(context: Context): Account? {
        val sharedPrefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)

        val signedInAccount = GoogleSignIn.getLastSignedInAccount(context)?.account
        if (signedInAccount?.name?.isNotBlank() == true) {
            sharedPrefs.edit().putString(UserRepository.KEY_USER_EMAIL, signedInAccount.name).apply()
            return signedInAccount
        }

        val emailFromPrefs = sharedPrefs
            .getString(UserRepository.KEY_USER_EMAIL, null)
            ?.trim()
            ?.takeIf { it.contains("@") }
            ?: return null

        return try {
            Account(emailFromPrefs, "com.google")
        } catch (_: Exception) {
            null
        }
    }
}
