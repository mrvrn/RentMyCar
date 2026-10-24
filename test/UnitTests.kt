package test

import com.rentmycar.domain.user.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitTests {
    @Test
    fun userRolesUseEnglishNames() {
        assertEquals(UserRole.OWNER, UserRole.fromString("OWNER"))
        assertEquals(UserRole.RENTER, UserRole.fromString("RENTER"))
        assertEquals(UserRole.OWNER, UserRole.fromString("AANBIEDER"))
        assertEquals(UserRole.RENTER, UserRole.fromString("HUURDER"))
    }
}
