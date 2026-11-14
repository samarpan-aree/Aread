package com.aread.app.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object Errors {
    fun friendly(t: Throwable): String {
        var root: Throwable = t
        while (root.cause != null && root.cause !== root) root = root.cause!!
        if (root is UnknownHostException || root is ConnectException || root is SocketTimeoutException) {
            return "No internet connection. Check your network and try again."
        }
        val m = (t.message.orEmpty() + " " + root.message.orEmpty()).lowercase()
        return when {
            m.contains("not set up") -> t.message.orEmpty()
            m.contains("invalid login") || m.contains("invalid_credentials") -> "Wrong email or password. Check them and try again."
            m.contains("already registered") || m.contains("already been registered") || m.contains("user_already_exists") ->
                "This email already has an account. Tap Sign in instead."
            m.contains("email not confirmed") || m.contains("email_not_confirmed") ->
                "Please confirm your email first. Open the link we sent you, then sign in."
            m.contains("weak_password") || m.contains("password should") -> "Choose a stronger password with at least 6 characters."
            m.contains("signup") && m.contains("disabled") -> "New sign-ups are paused right now. Please try again later."
            m.contains("rate limit") || m.contains("over_email_send_rate_limit") || m.contains("too many") ->
                "Too many attempts. Please wait a minute and try again."
            m.contains("unsupported phone provider") || (m.contains("phone") && m.contains("provider")) ->
                "Phone sign-in isn't available yet. Please use email or Google."
            m.contains("provider is not enabled") || m.contains("provider_disabled") || (m.contains("provider") && m.contains("enabled")) ->
                "This sign-in method isn't available yet. Please use email."
            m.contains("otp") && (m.contains("expired") || m.contains("invalid")) -> "That code is wrong or has expired. Request a new one."
            m.contains("jwt") || m.contains("unauthorized") || m.contains("401") -> "Your session expired. Please sign in again."
            m.contains("429") -> "Too many requests. Please wait a moment."
            m.contains("500") || m.contains("502") || m.contains("503") -> "Our server is busy. Please try again in a moment."
            m.contains("cancel") -> "Cancelled."
            else -> "Something went wrong. Please try again."
        }
    }

    fun tutor(t: Throwable): String {
        val m = (t.message.orEmpty()).lowercase()
        return when {
            m.contains("unknownhost") || m.contains("unable to resolve") || m.contains("timeout") || m.contains("connect") ->
                "I can't connect right now. Check your internet and send that again."
            m.contains("401") || m.contains("jwt") -> "Please sign in again so I can help you."
            m.contains("429") -> "You've asked a lot today. Take a short break and come back soon."
            else -> "I'm having trouble thinking right now. Please try again in a moment."
        }
    }
}
