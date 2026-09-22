package murat.com.saasproject

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `app/google-services.json` gerçek Firebase projesine ait olmalı.
 * Placeholder app id veya sahte OAuth client burada fail eder.
 */
class FirebaseConfigTest {

    @Test
    fun googleServicesJsonMatchesIosFirebaseProject() {
        val file = File("google-services.json")
        assertTrue("app/google-services.json missing", file.exists())

        val root = JSONObject(file.readText())
        val project = root.getJSONObject("project_info")
        assertEquals("saasproject-23a1f", project.getString("project_id"))
        assertEquals("849412597288", project.getString("project_number"))

        val client = root.getJSONArray("client").getJSONObject(0)
        val info = client.getJSONObject("client_info")
        val appId = info.getString("mobilesdk_app_id")
        val packageName = info.getJSONObject("android_client_info").getString("package_name")

        assertEquals("murat.com.saasproject", packageName)
        assertTrue(appId.startsWith("1:849412597288:android:"))
        assertFalse(appId.contains("0000000000000000000000"))

        val oauth = client.getJSONArray("oauth_client")
        assertTrue("web OAuth client (type 3) required for Google Sign-In", oauth.length() > 0)
        var hasWebClient = false
        for (i in 0 until oauth.length()) {
            if (oauth.getJSONObject(i).getInt("client_type") == 3) {
                hasWebClient = true
                break
            }
        }
        assertTrue(hasWebClient)
    }
}
