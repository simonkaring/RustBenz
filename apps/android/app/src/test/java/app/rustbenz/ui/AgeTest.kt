package app.rustbenz.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AgeTest {
    private val now = Instant.ofEpochSecond(1_000_000)

    @Test
    fun formatsVehicleSideAge() {
        assertEquals("time unknown", age(null, now))
        assertEquals("just now", age(now.minusSeconds(5), now))
        assertEquals("10 min ago", age(now.minusSeconds(600), now))
        assertEquals("2 h ago", age(now.minusSeconds(7_200), now))
        assertEquals("3 days ago", age(now.minusSeconds(3 * 86_400), now))
        assertEquals("just now", age(now.plusSeconds(30), now)) // clock skew
    }
}
