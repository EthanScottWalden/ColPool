package io.github.ethanscottwalden.colpool.ui.notifications

import io.github.ethanscottwalden.colpool.ui.bannerTapOpensTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerTapTest {
    @Test fun opensTheTabFromTheShellAndFromDetailsScreens() = assertTrue(bannerTapOpensTab(creatingSeries = false, reviewDraftId = null))

    @Test fun neverLeavesCreateSeriesOrReview() {
        assertFalse(bannerTapOpensTab(creatingSeries = true, reviewDraftId = null))
        assertFalse(bannerTapOpensTab(creatingSeries = false, reviewDraftId = "draft1"))
        assertFalse(bannerTapOpensTab(creatingSeries = true, reviewDraftId = "draft1"))
    }
}
