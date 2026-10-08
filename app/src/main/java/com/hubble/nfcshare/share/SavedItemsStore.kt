package com.hubble.nfcshare.share

import android.content.Context
import com.luigivampa92.ndefemulation.ndef.ContactNdefData
import org.json.JSONArray
import org.json.JSONObject

// Persists the user's pinned links and remembered WiFi networks, shared by the
// main app UI and the widget configuration screen.
object SavedItemsStore {

    data class SavedWifiNetwork(val ssid: String, val password: String, val isOpen: Boolean)

    // Empty strings mean "not set" for the optional fields.
    data class SavedContact(val first: String, val last: String, val phone: String, val email: String) {
        val displayName get() = "$first $last".trim()

        fun toNdefData() = ContactNdefData(first, last.ifBlank { null }, phone.ifBlank { null }, email.ifBlank { null })
    }

    private const val PREFS_CONTACTS = "saved_contacts"
    private const val KEY_CONTACTS_JSON = "contacts"
    private const val MAX_SAVED_CONTACTS = 20

    private const val PREFS_LINKS = "saved_links"
    private const val KEY_LINKS_JSON = "links"
    private const val MAX_SAVED_LINKS = 20

    private const val PREFS_WIFI = "saved_wifi_networks"
    private const val KEY_NETWORKS_JSON = "networks"
    private const val MAX_SAVED_NETWORKS = 50

    fun loadLinks(context: Context): List<String> {
        val raw = linkPrefs(context).getString(KEY_LINKS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLink(context: Context, raw: String) {
        val remaining = loadLinks(context).filterNot { it == raw }
        persistLinks(context, (listOf(raw) + remaining).take(MAX_SAVED_LINKS))
    }

    fun removeLink(context: Context, raw: String) {
        persistLinks(context, loadLinks(context).filterNot { it == raw })
    }

    private fun persistLinks(context: Context, links: List<String>) {
        val array = JSONArray()
        links.forEach { array.put(it) }
        linkPrefs(context).edit().putString(KEY_LINKS_JSON, array.toString()).apply()
    }

    fun loadNetworks(context: Context): List<SavedWifiNetwork> {
        val raw = wifiPrefs(context).getString(KEY_NETWORKS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                SavedWifiNetwork(obj.getString("ssid"), obj.getString("password"), obj.getBoolean("open"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun upsertNetwork(context: Context, ssid: String, password: String, isOpen: Boolean) {
        val remaining = loadNetworks(context).filterNot { it.ssid == ssid }
        persistNetworks(context, (listOf(SavedWifiNetwork(ssid, password, isOpen)) + remaining).take(MAX_SAVED_NETWORKS))
    }

    // Adds networks read via Shizuku; networks already in the list keep their position
    // but get the (authoritative) password from the system.
    fun importNetworks(context: Context, imported: List<SavedWifiNetwork>): Int {
        val existing = loadNetworks(context)
        val byName = imported.associateBy { it.ssid }
        val updated = existing.map { byName[it.ssid] ?: it }
        val added = imported.filter { network -> existing.none { it.ssid == network.ssid } }
        persistNetworks(context, (updated + added).take(MAX_SAVED_NETWORKS))
        return added.size
    }

    fun removeNetwork(context: Context, ssid: String) {
        persistNetworks(context, loadNetworks(context).filterNot { it.ssid == ssid })
    }

    private fun persistNetworks(context: Context, networks: List<SavedWifiNetwork>) {
        val array = JSONArray()
        networks.forEach { array.put(JSONObject().put("ssid", it.ssid).put("password", it.password).put("open", it.isOpen)) }
        wifiPrefs(context).edit().putString(KEY_NETWORKS_JSON, array.toString()).apply()
    }

    fun loadContacts(context: Context): List<SavedContact> {
        val raw = contactPrefs(context).getString(KEY_CONTACTS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                SavedContact(obj.getString("first"), obj.optString("last"), obj.optString("phone"), obj.optString("email"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveContact(context: Context, contact: SavedContact) {
        val remaining = loadContacts(context).filterNot { it == contact }
        persistContacts(context, (listOf(contact) + remaining).take(MAX_SAVED_CONTACTS))
    }

    fun removeContact(context: Context, contact: SavedContact) {
        persistContacts(context, loadContacts(context).filterNot { it == contact })
    }

    private fun persistContacts(context: Context, contacts: List<SavedContact>) {
        val array = JSONArray()
        contacts.forEach {
            array.put(JSONObject().put("first", it.first).put("last", it.last).put("phone", it.phone).put("email", it.email))
        }
        contactPrefs(context).edit().putString(KEY_CONTACTS_JSON, array.toString()).apply()
    }

    private fun linkPrefs(context: Context) = context.getSharedPreferences(PREFS_LINKS, Context.MODE_PRIVATE)
    private fun contactPrefs(context: Context) = context.getSharedPreferences(PREFS_CONTACTS, Context.MODE_PRIVATE)
    private fun wifiPrefs(context: Context) = context.getSharedPreferences(PREFS_WIFI, Context.MODE_PRIVATE)
}
