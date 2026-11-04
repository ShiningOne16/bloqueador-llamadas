package com.ejemplo.bloqueador

import android.content.Intent
import android.net.Uri
import java.util.UUID

object SmsNavigation {
    fun parse(intent: Intent?): SmsOpenRequest? {
        intent ?: return null
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri?.scheme == "bloqueador" && uri.host == "sms") {
            return SmsOpenRequest(UUID.randomUUID().toString(), threadId = uri.lastPathSegment?.toLongOrNull(), compose = false)
        }
        if (intent.action != Intent.ACTION_SENDTO || uri?.scheme !in setOf("sms", "smsto", "mms", "mmsto")) return null
        val raw = uri?.encodedSchemeSpecificPart.orEmpty()
        val address = Uri.decode(raw.substringBefore('?')).take(100)
        val query = raw.substringAfter('?', "")
        val body = intent.getStringExtra("sms_body") ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: Uri.parse("https://local.invalid/?$query").getQueryParameter("body").orEmpty()
        return SmsOpenRequest(UUID.randomUUID().toString(), address, body.take(10_000))
    }
}
