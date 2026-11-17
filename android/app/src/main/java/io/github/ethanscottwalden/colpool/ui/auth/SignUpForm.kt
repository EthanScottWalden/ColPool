package io.github.ethanscottwalden.colpool.ui.auth

enum class SignUpFieldError { FirstName, LastName, Email, Password, Consent }

/** Client-side checks that mirror the sign-up callable (functions/src/signUp.ts, shared/auth/reasons.ts). The server still decides. */
object SignUpForm {
    const val CBU_EMAIL_SUFFIX = "@calbaptist.edu"
    const val PASSWORD_MIN_LENGTH = 8
    const val NAME_MAX = 30

    fun normalizeEmail(email: String) = email.trim().lowercase()

    fun validEmail(email: String): Boolean {
        val e = normalizeEmail(email)
        if (!e.endsWith(CBU_EMAIL_SUFFIX)) return false
        val local = e.removeSuffix(CBU_EMAIL_SUFFIX)
        return local.isNotEmpty() && local.none { it.isWhitespace() || it == '@' }
    }

    fun validName(name: String): Boolean = name.trim().let { it.isNotEmpty() && it.length <= NAME_MAX }

    fun validate(firstName: String, lastName: String, email: String, password: String, consent: Boolean): Set<SignUpFieldError> =
        buildSet {
            if (!validName(firstName)) add(SignUpFieldError.FirstName)
            if (!validName(lastName)) add(SignUpFieldError.LastName)
            if (!validEmail(email)) add(SignUpFieldError.Email)
            if (password.length < PASSWORD_MIN_LENGTH) add(SignUpFieldError.Password)
            if (!consent) add(SignUpFieldError.Consent)
        }
}
