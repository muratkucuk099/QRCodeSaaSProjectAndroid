package murat.com.saasproject.data.model

import murat.com.saasproject.domain.config.QrConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    @Test
    fun pointsPayloadRoundTripKeepsIntegerPoints() {
        val original = QrPayload(qrCode = "abc", businessId = "biz-1", points = 10)
        val parsed = QrPayload.fromJson(original.toJson())
        assertEquals(original, parsed)
        assertFalse(parsed!!.isRewardRedemption)
        assertTrue(original.toJson().contains("\"points\":10"))
        assertFalse(original.toJson().contains("10.0"))
    }

    @Test
    fun rewardPayloadOmitsNullUserId() {
        val json = QrPayload(
            qrCode = "xyz",
            businessId = "biz-1",
            points = -50,
            rewardId = "r1",
            rewardName = "Kahve"
        ).toJson()
        assertFalse(json.contains("userId"))
        val parsed = QrPayload.fromJson(json)!!
        assertTrue(parsed.isRewardRedemption)
        assertEquals(-50, parsed.points)
    }

    @Test
    fun invalidJsonOrMissingFieldsReturnNull() {
        assertNull(QrPayload.fromJson("not-json"))
        assertNull(QrPayload.fromJson("""{"qrCode":"a","businessId":"b"}"""))
        assertNull(QrPayload.fromJson("""{"qrCode":"","businessId":"b","points":1}"""))
    }

    @Test
    fun displayAndServerTtlMatchIos() {
        assertEquals(10, QrConfig.DISPLAY_COUNTDOWN_SECONDS)
        assertEquals(10L, QrConfig.VALIDITY_MINUTES)
    }
}
