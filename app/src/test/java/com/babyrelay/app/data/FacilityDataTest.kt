package com.babyrelay.app.data

import com.babyrelay.app.Fx
import com.babyrelay.app.data.facility.DataQuality
import com.babyrelay.app.data.facility.FacilityLoad
import com.babyrelay.app.data.facility.SnapshotParser
import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.DataOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FacilityDataTest {
    private fun asset() = File("src/main/assets/facilities_snapshot.json").readText(Charsets.UTF_8)

    @Test fun `번들된 스냅샷은 SAMPLE로 표시되고 파싱된다`() {
        val loaded = SnapshotParser.parse(asset()) as FacilityLoad.Loaded
        val snap = loaded.snapshot
        assertEquals("SAMPLE", snap.meta.kind)
        assertTrue(snap.facilities.isNotEmpty())
        assertTrue("모든 시설이 합성 데이터로 표시", snap.facilities.all { it.origin == DataOrigin.SAMPLE })
        assertTrue("마지막 목적지는 데이터 없음 시연용", snap.destinations.size >= 2)
    }

    @Test fun `번들된 스냅샷의 품질 지표`() {
        val q = DataQuality.of((SnapshotParser.parse(asset()) as FacilityLoad.Loaded).snapshot.facilities)
        assertEquals(14, q.total)
        assertEquals(14, q.validCoordinates)
        assertEquals(0, q.duplicateIds)
        assertEquals(1, q.missingKind)
        assertEquals(1, q.missingAmenities)
        assertEquals(mapOf(DataOrigin.SAMPLE to 14), q.byOrigin)
        assertEquals("2025-06-01", q.oldestVerified)
    }

    @Test fun `품질 지표는 중복과 잘못된 좌표를 센다`() {
        val a = Fx.facility("a", 0.5)
        val list = listOf(a, a.copy(), Fx.facility("b", 0.4).copy(lat = 0.0, lon = 0.0), Fx.facility("c", 0.3, kind = null))
        val q = DataQuality.of(list)
        assertEquals(4, q.total)
        assertEquals(3, q.validCoordinates)
        assertEquals(1, q.duplicateIds)
        assertEquals(1, q.missingKind)
        assertEquals(0.75, q.validCoordinateRatio, 1e-9)
    }

    @Test fun `빈 목록의 품질 지표는 0으로 안전하다`() {
        val q = DataQuality.of(emptyList())
        assertEquals(0, q.total)
        assertEquals(0.0, q.validCoordinateRatio, 0.0)
        assertEquals(null, q.oldestVerified)
    }

    @Test fun `깨진 JSON은 예외 대신 실패 결과가 된다`() {
        assertTrue(SnapshotParser.parse("{ not json") is FacilityLoad.Failed)
        assertTrue(SnapshotParser.parse("") is FacilityLoad.Failed)
    }

    @Test fun `알 수 없는 필드가 있어도 파싱된다`() {
        val text = asset().replace("\"origin\": \"SAMPLE\"", "\"origin\": \"SAMPLE\", \"futureField\": 1")
        assertTrue(SnapshotParser.parse(text) is FacilityLoad.Loaded)
    }

    @Test fun `시설 보유 조건 타입 변환`() {
        val f = (SnapshotParser.parse(asset()) as FacilityLoad.Loaded).snapshot.facilities.first()
        assertTrue(f.amenities.contains(Amenity.NURSING_ROOM))
    }
}
