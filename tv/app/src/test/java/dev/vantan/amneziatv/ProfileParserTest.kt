package dev.vantan.amneziatv
import org.junit.Test
import org.junit.Assert.*
import org.json.*
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
class ProfileParserTest {
 private fun key(json:JSONObject):String {
  val raw=json.toString().toByteArray();val out=ByteArrayOutputStream()
  out.write(ByteBuffer.allocate(4).putInt(raw.size).array());DeflaterOutputStream(out).use{it.write(raw)}
  return "vpn://"+Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
 }
 private fun profile():JSONObject {
  val key=Base64.getEncoder().encodeToString(ByteArray(32){1})
  val data=JSONObject().put("client_ip","10.0.0.2/32").put("hostName","192.0.2.1").put("port",1234)
   .put("client_priv_key",key).put("server_pub_key",key).put("allowed_ips",JSONArray(listOf("0.0.0.0/0","::/0")))
  for(k in listOf("Jc","Jmin","Jmax","S1","S2","H1","H2","H3","H4"))data.put(k,1)
  data.put("HeaderProtectionKey",key).put("I1","<b 0x1234>")
  return JSONObject().put("description","Test server").put("defaultContainer","amnezia-awg2")
   .put("containers",JSONArray().put(JSONObject().put("container","amnezia-awg2").put("awg",JSONObject().put("last_config",data.toString()))))
 }
 @Test fun compressedAwg2RetainsExtensionAndRoutes(){
  val p=ProfileParser.parse(key(profile()));val d=p.getJSONObject("config").getJSONObject("awg_config_data")
  assertEquals("Test server",p.getString("name"));assertTrue(d.has("HeaderProtectionKey"));assertEquals("<b 0x1234>",d.getString("I1"));assertEquals(2,d.getJSONArray("allowed_ips").length())
 }
 @Test fun rejectsOtherProtocols(){try{ProfileParser.parse(key(JSONObject().put("containers",JSONArray().put(JSONObject().put("xray",JSONObject())))));fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("AmneziaWG"))}}
 @Test fun rejectsBrokenKeyMaterial(){val p=profile();val a=p.getJSONArray("containers").getJSONObject(0).getJSONObject("awg");val d=JSONObject(a.getString("last_config")).put("client_priv_key","abcd");a.put("last_config",d.toString());try{ProfileParser.parse(key(p));fail()}catch(e:IllegalArgumentException){}}
 @Test fun rejectsOversizedDecompression(){val bytes=ByteBuffer.allocate(8).putInt(1_000_000).putInt(0).array();try{ProfileParser.parse("vpn://"+Base64.getUrlEncoder().encodeToString(bytes));fail()}catch(e:IllegalArgumentException){}}
 @Test fun rejectsIncompleteConfig(){val p=profile();p.getJSONArray("containers").getJSONObject(0).getJSONObject("awg").put("last_config","{}");try{ProfileParser.parse(key(p));fail()}catch(e:IllegalArgumentException){}}
}
