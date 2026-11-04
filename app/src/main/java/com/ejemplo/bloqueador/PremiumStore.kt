package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Vista previa local. No representa ni verifica una compra o suscripción. */
class PremiumStore(context: Context) {
    private val prefs = context.getSharedPreferences("premium_profiles", Context.MODE_PRIVATE)

    fun read(): PremiumConfiguration {
        val raw = prefs.getString("configuration", null) ?: return PremiumConfiguration()
        return try {
            val root = JSONObject(raw)
            val array = root.getJSONArray("profiles")
            val profiles = (0 until array.length()).map { index ->
                val p = array.getJSONObject(index)
                val s = p.getJSONObject("schedule")
                val days = s.getJSONArray("days")
                val exceptions = p.optJSONArray("exceptions") ?: JSONArray()
                val contacts = p.optJSONArray("scheduledContacts") ?: JSONArray()
                ProtectionProfile(
                    id = p.getString("id"), name = p.getString("name"),
                    blockUnknown = p.optBoolean("blockUnknown", true),
                    blockHidden = p.optBoolean("blockHidden", true),
                    allowRepeat = p.optBoolean("allowRepeat", false),
                    silenceUnknownSms = p.optBoolean("silenceUnknownSms", true),
                    includeBusinessSms = p.optBoolean("includeBusinessSms", false),
                    schedule = ProtectionSchedule(
                        s.getBoolean("enabled"),
                        (0 until days.length()).map { days.getInt(it) }.toSet(),
                        s.getInt("start"), s.getInt("end")
                    ),
                    exceptions = (0 until exceptions.length()).map { i ->
                        val e = exceptions.getJSONObject(i)
                        NumberException(e.getString("number"), e.optString("label"), NumberAction.valueOf(e.getString("action")))
                    }.filter { PremiumRules.validNumber(it.number) }.distinctBy { PremiumRules.numberKey(it.number) },
                    scheduledContacts = (0 until contacts.length()).map { i ->
                        val c = contacts.getJSONObject(i)
                        ScheduledContact(c.getString("number"), c.optString("name"))
                    }.filter { PremiumRules.validNumber(it.number) }.distinctBy { PremiumRules.numberKey(it.number) }
                )
            }
            val active = root.optString("active").takeIf { id -> profiles.any { it.id == id } }
            PremiumConfiguration(profiles, active)
        } catch (_: Exception) {
            PremiumConfiguration() // Si los datos están dañados, sigue vigente la protección básica.
        }
    }

    fun save(configuration: PremiumConfiguration) {
        val profiles = JSONArray()
        configuration.profiles.forEach { p ->
            val exceptions = JSONArray()
            p.exceptions.forEach { e ->
                exceptions.put(JSONObject().put("number", e.number).put("label", e.label).put("action", e.action.name))
            }
            profiles.put(JSONObject().put("id", p.id).put("name", p.name)
                .put("blockUnknown", p.blockUnknown).put("blockHidden", p.blockHidden).put("allowRepeat", p.allowRepeat)
                .put("silenceUnknownSms", p.silenceUnknownSms).put("includeBusinessSms", p.includeBusinessSms)
                .put("schedule", JSONObject().put("enabled", p.schedule.enabled)
                    .put("days", JSONArray(p.schedule.days.sorted())).put("start", p.schedule.startMinute).put("end", p.schedule.endMinute))
                .put("exceptions", exceptions)
                .put("scheduledContacts", JSONArray().also { contacts ->
                    p.scheduledContacts.forEach { c -> contacts.put(JSONObject().put("number", c.number).put("name", c.name)) }
                }))
        }
        prefs.edit().putString("configuration", JSONObject().put("profiles", profiles)
            .put("active", configuration.activeProfileId ?: "").toString()).apply()
    }
}
