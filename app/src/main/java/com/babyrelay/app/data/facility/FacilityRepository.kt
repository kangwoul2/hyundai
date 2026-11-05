package com.babyrelay.app.data.facility

import android.content.Context
import com.babyrelay.app.domain.model.DataOrigin
import com.babyrelay.app.domain.model.Facility
import com.babyrelay.app.domain.model.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SnapshotMeta(
    val dataset: String,
    /** 데이터 성격: SAMPLE(합성) 또는 PUBLIC_DATA(실수집) */
    val kind: String,
    val generatedOn: String,
    val note: String,
)

@Serializable
data class DemoOrigin(val lat: Double, val lon: Double, val label: String)

@Serializable
data class FacilitySnapshot(
    val meta: SnapshotMeta,
    val demoOrigin: DemoOrigin,
    /** 마지막 항목은 시설 데이터가 없는 지역(데이터 없음 시나리오)이다. */
    val destinations: List<Place>,
    val facilities: List<Facility>,
)

sealed interface FacilityLoad {
    data class Loaded(val snapshot: FacilitySnapshot) : FacilityLoad
    data class Failed(val reason: String) : FacilityLoad
}

interface FacilityRepository {
    fun load(): FacilityLoad
}

object SnapshotParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): FacilityLoad = try {
        FacilityLoad.Loaded(json.decodeFromString(FacilitySnapshot.serializer(), text))
    } catch (t: Throwable) {
        FacilityLoad.Failed("시설 데이터 파싱 실패: ${t.javaClass.simpleName}")
    }
}

class AssetFacilityRepository(private val context: Context) : FacilityRepository {
    override fun load(): FacilityLoad = try {
        val text = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        SnapshotParser.parse(text)
    } catch (t: Throwable) {
        FacilityLoad.Failed("시설 데이터 파일을 열 수 없음: ${t.javaClass.simpleName}")
    }

    private companion object {
        const val ASSET = "facilities_snapshot.json"
    }
}

/** 데이터 품질 지표. tools/collect_facilities.py와 같은 정의를 사용한다. */
data class DataQuality(
    val total: Int,
    val validCoordinates: Int,
    val duplicateIds: Int,
    val missingKind: Int,
    val missingAmenities: Int,
    val byOrigin: Map<DataOrigin, Int>,
    val oldestVerified: String?,
    val newestVerified: String?,
) {
    val validCoordinateRatio: Double get() = if (total == 0) 0.0 else validCoordinates.toDouble() / total
    val missingKindRatio: Double get() = if (total == 0) 0.0 else missingKind.toDouble() / total

    companion object {
        fun of(list: List<Facility>): DataQuality {
            val ids = list.map { it.id }
            val valid = list.count {
                it.lat.isFinite() && it.lon.isFinite() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0 && !(it.lat == 0.0 && it.lon == 0.0)
            }
            val dates = list.map { it.verifiedOn }.filter { it.isNotBlank() }.sorted()
            return DataQuality(
                total = list.size,
                validCoordinates = valid,
                duplicateIds = ids.size - ids.toSet().size,
                missingKind = list.count { it.kind.isNullOrBlank() },
                missingAmenities = list.count { it.amenities.isEmpty() },
                byOrigin = list.groupingBy { it.origin }.eachCount(),
                oldestVerified = dates.firstOrNull(),
                newestVerified = dates.lastOrNull(),
            )
        }
    }
}
