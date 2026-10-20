package dev.vantan.amneziatv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Independent of Android APIs so malformed/untrusted imports can be unit tested.
object ProfileParser {
 private const val LIMIT = 512 * 1024
 fun parse(raw: String): JSONObject {
  require(raw.length <= LIMIT) { "Слишком большой ключ" }
  val text = raw.trim()
  val decoded = if (text.startsWith("vpn://")) {
   val bytes = Base64.getUrlDecoder().decode(text.removePrefix("vpn://"))
   require(bytes.size > 4)
   // Qt qCompress: 4-byte big-endian original length, then zlib.
   val expected = java.nio.ByteBuffer.wrap(bytes,0,4).int
   require(expected in 1..LIMIT) { "Неверный размер ключа" }
   val expanded = InflaterInputStream(ByteArrayInputStream(bytes,4,bytes.size-4)).use { stream -> val output=java.io.ByteArrayOutputStream(); val chunk=ByteArray(4096); var total=0; while(true) { val n=stream.read(chunk);if(n<0)break;total+=n;require(total<=LIMIT){"Слишком большой ключ"};output.write(chunk,0,n) };output.toByteArray() }
   require(expanded.size == expected && expanded.size <= LIMIT) { "Повреждённый ключ" }
   String(expanded,Charsets.UTF_8)
  } else text
  val source = JSONObject(decoded)
  val containers = source.optJSONArray("containers") ?: throw IllegalArgumentException("Нужен ключ Amnezia vpn://")
  val preferred = source.optString("defaultContainer")
  val candidates = (0 until containers.length()).map { containers.getJSONObject(it) }
  val container = candidates.firstOrNull { it.optString("container") == preferred && it.has("awg") }
   ?: candidates.firstOrNull { it.has("awg") }
   ?: throw IllegalArgumentException("Эта TV-сборка поддерживает AmneziaWG. Другие протоколы открывайте в обычной Amnezia.")
  val awg = container.getJSONObject("awg")
  val last = awg.get("last_config")
  val data = if (last is JSONObject) JSONObject(last.toString()) else JSONObject(last.toString())
  // Match the upstream Android AWG protocol schema; retain all AWG 2/3 fields.
  awg.keys().forEach { k -> if (k != "last_config" && !data.has(k)) data.put(k,awg.get(k)) }
  for (field in listOf("client_ip","hostName","port","client_priv_key","server_pub_key","allowed_ips","Jc","Jmin","Jmax","S1","S2","H1","H2","H3","H4")) require(data.has(field)) { "Неполный ключ: $field" }
  for (field in listOf("client_priv_key","server_pub_key")) require(Base64.getDecoder().decode(data.getString(field)).size == 32) { "Повреждённый ключ" }
  data.remove("config") // avoid retaining a redundant plaintext config
  return JSONObject().put("id",UUID.randomUUID().toString())
   .put("name",source.optString("description","Мой сервер").take(100))
   .put("config",JSONObject().put("protocol","awg").put("description",source.optString("description","Мой сервер"))
    .put("awg_config_data",data).put("dns1",source.optString("dns1","1.1.1.1")).put("dns2",source.optString("dns2","1.0.0.1")))
 }
}

class ProfileStore(private val context: Context) {
 private val prefs = context.getSharedPreferences("tv-private",Context.MODE_PRIVATE)
 private val alias = "amnezia-tv-profiles-v1"
 private fun key(): SecretKey {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (ks.getKey(alias,null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun profiles(): List<JSONObject> {
  val value = prefs.getString("profiles",null) ?: return emptyList()
  val bytes = Base64.getDecoder().decode(value)
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
  val json = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
  return (0 until json.length()).map { json.getJSONObject(it) }
 }
 private fun save(profiles: List<JSONObject>) {
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
  val bytes = JSONArray(profiles).toString().toByteArray(Charsets.UTF_8)
  val sealed = cipher.iv + cipher.doFinal(bytes)
  check(prefs.edit().putString("profiles",Base64.getEncoder().encodeToString(sealed)).commit()) { "Не удалось сохранить сервер" }
 }
 fun add(raw: String): JSONObject {
  val parsed = ProfileParser.parse(raw)
  val all=profiles().toMutableList()
  val same=all.indexOfFirst { it.getJSONObject("config").toString()==parsed.getJSONObject("config").toString() }
  if(same>=0) { select(all[same].getString("id"));return all[same] }
  all.add(parsed);save(all);select(parsed.getString("id"));return parsed
 }
 fun selected(): JSONObject? { val all=profiles();return all.firstOrNull { it.optString("id")==prefs.getString("selected",null) } ?: all.firstOrNull() }
 fun select(id: String) { check(profiles().any { it.getString("id")==id });prefs.edit().putString("selected",id).apply() }
 fun deleteSelected() { val id=selected()?.getString("id");save(profiles().filter { it.getString("id")!=id });prefs.edit().remove("selected").apply() }
}
