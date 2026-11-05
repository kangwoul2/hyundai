package com.babyrelay.app.domain

import com.babyrelay.app.Fx
import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.DataOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StopRecommenderTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val needs = setOf(Amenity.NURSING_ROOM, Amenity.DIAPER_TABLE)

    private fun req(
        origin: LatLon? = LatLon(Fx.ORIGIN_LAT, Fx.ORIGIN_LON),
        rest: Int = 60,
        needs: Set<Amenity> = this.needs,
        dest: com.babyrelay.app.domain.model.Place = Fx.daejeon,
    ) = RecommendRequest(origin, dest, rest, needs, today)

    @Test fun `필수 시설 조건을 모두 갖춘 시설만 후보가 된다`() {
        val list = listOf(
            Fx.facility("a", 0.45, amenities = setOf(Amenity.NURSING_ROOM)),                 // 기저귀 교환대 없음
            Fx.facility("b", 0.45, amenities = setOf(Amenity.DIAPER_TABLE, Amenity.PARKING)), // 수유실 없음
            Fx.facility("c", 0.46, amenities = needs + Amenity.PARKING),
        )
        val r = StopRecommender.recommend(list, req())
        assertEquals(listOf("c"), r.candidates.map { it.facility.id })
        assertEquals(Basis.ROUTE_LINE, r.basis)
    }

    @Test fun `최대 2개만 추천하고 희망 시각에 가까운 순서로 정렬한다`() {
        val list = listOf(
            Fx.facility("far", 0.85),
            Fx.facility("best", 0.46),   // 60분 × 60km/h ≈ 60km ≈ 직선 약 46%
            Fx.facility("near", 0.40),
            Fx.facility("mid", 0.60),
        )
        val r = StopRecommender.recommend(list, req())
        assertEquals(2, r.candidates.size)
        assertEquals("best", r.candidates[0].facility.id)
        assertTrue(r.candidates[0].score >= r.candidates[1].score)
    }

    @Test fun `희망 휴식 시각을 바꾸면 더 먼 지점이 선호된다`() {
        val list = listOf(Fx.facility("early", 0.25), Fx.facility("late", 0.75))
        assertEquals("early", StopRecommender.recommend(list, req(rest = 30)).candidates.first().facility.id)
        assertEquals("late", StopRecommender.recommend(list, req(rest = 90)).candidates.first().facility.id)
    }

    @Test fun `점수가 같으면 id 순으로 결정적이다`() {
        val list = listOf(Fx.facility("b", 0.46), Fx.facility("a", 0.46), Fx.facility("c", 0.46))
        val first = StopRecommender.recommend(list, req())
        val second = StopRecommender.recommend(list.reversed(), req())
        assertEquals(listOf("a", "b"), first.candidates.map { it.facility.id })
        assertEquals(first.candidates.map { it.facility.id }, second.candidates.map { it.facility.id })
        assertEquals(first.candidates.map { it.score }, second.candidates.map { it.score })
    }

    @Test fun `경로에서 너무 먼 시설과 경로 밖 시설은 제외된다`() {
        val list = listOf(
            Fx.facility("farOff", 0.46, off = 0.5),      // 약 44km 옆
            Fx.facility("behind", -0.2),                  // 출발점 뒤
            Fx.facility("beyond", 1.2),                   // 도착점 너머
            Fx.facility("ok", 0.5, off = 0.05),           // 약 4km 옆
        )
        val r = StopRecommender.recommend(list, req())
        assertEquals(listOf("ok"), r.candidates.map { it.facility.id })
    }

    @Test fun `추천 이유는 실제 계산값과 일치한다`() {
        val r = StopRecommender.recommend(listOf(Fx.facility("x", 0.46, off = 0.03)), req())
        val c = r.candidates.single()
        val along = String.format(Locale.US, "%.1f", c.alongKm!!)
        val off = String.format(Locale.US, "%.1f", c.offKm!!)
        val joined = c.reasons.joinToString("\n")
        assertTrue(joined, "수유실·기저귀 교환대 정보 있음" in joined)
        assertTrue(joined, "직선 약 ${along}km 지점" in joined)
        assertTrue(joined, "희망 60분" in joined && "평균 60km/h 가정" in joined)
        assertTrue(joined, "직선에서 ${off}km" in joined && "실제 우회 시간 아님" in joined)
        assertTrue(joined, "샘플 데이터 · 확인일 2026-10-20" in joined)
        assertTrue("우회 시간 수치를 만들어 내지 않는다", Regex("우회\\s*\\d+\\s*분").find(joined) == null)
    }

    @Test fun `확인일이 1년 넘은 시설은 사유에 표시되고 점수가 낮아진다`() {
        val fresh = Fx.facility("fresh", 0.46)
        val stale = Fx.facility("stale", 0.46, verifiedOn = "2025-01-01")
        val r = StopRecommender.recommend(listOf(stale, fresh), req())
        assertEquals("fresh", r.candidates[0].facility.id)
        assertTrue(r.candidates[1].reasons.any { "1년 이상 지남" in it })
        assertTrue(r.candidates[0].score > r.candidates[1].score)
    }

    @Test fun `출처에 따라 신뢰도가 반영된다`() {
        val pub = Fx.facility("pub", 0.46, origin = DataOrigin.PUBLIC_DATA)
        val sample = Fx.facility("smp", 0.46, origin = DataOrigin.SAMPLE)
        val r = StopRecommender.recommend(listOf(sample, pub), req())
        assertEquals("pub", r.candidates[0].facility.id)
    }

    @Test fun `현재 위치가 없으면 목적지 근처 후보로 표시하고 안내 문구를 준다`() {
        val near = Fx.facility("near", 0.97)
        val r = StopRecommender.recommend(listOf(near, Fx.facility("mid", 0.5)), req(origin = null))
        assertEquals(Basis.NEAR_DESTINATION, r.basis)
        assertEquals(listOf("near"), r.candidates.map { it.facility.id })
        assertTrue(r.notice!!.contains("현재 위치"))
        assertTrue(r.candidates[0].reasons.any { "목적지에서 직선" in it })
    }

    @Test fun `시설 데이터가 비어 있으면 후보 없음과 사유를 돌려준다`() {
        val r = StopRecommender.recommend(emptyList(), req())
        assertTrue(r.candidates.isEmpty())
        assertEquals(Basis.NONE, r.basis)
        assertTrue(r.notice!!.contains("불러오지 못"))
    }

    @Test fun `데이터가 없는 지역 목적지는 후보 없음`() {
        val r = StopRecommender.recommend(listOf(Fx.facility("a", 0.5)), req(dest = Fx.gangneung))
        assertTrue(r.candidates.isEmpty())
        assertEquals(Basis.NONE, r.basis)
        assertTrue(r.notice!!.isNotBlank())
    }

    @Test fun `좌표가 잘못된 시설과 중복 id는 걸러진다`() {
        val bad = Fx.facility("bad", 0.5).copy(lat = Double.NaN)
        val zero = Fx.facility("zero", 0.5).copy(lat = 0.0, lon = 0.0)
        val dup1 = Fx.facility("dup", 0.46)
        val dup2 = Fx.facility("dup", 0.47)
        val r = StopRecommender.recommend(listOf(bad, zero, dup1, dup2), req())
        assertEquals(listOf("dup"), r.candidates.map { it.facility.id })
    }

    @Test fun `시설 정보가 아예 없는(미상) 시설은 조건이 있으면 후보가 아니다`() {
        val unknown = Fx.facility("unknown", 0.46, amenities = emptySet())
        val r = StopRecommender.recommend(listOf(unknown), req())
        assertTrue(r.candidates.isEmpty())
    }

    @Test fun `조건을 비우면 시설 보유 정보를 사유에 보여준다`() {
        val r = StopRecommender.recommend(listOf(Fx.facility("x", 0.46)), req(needs = emptySet()))
        assertTrue(r.candidates.single().reasons.first().contains("수유실"))
    }
}
