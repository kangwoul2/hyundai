package com.babyrelay.app.domain.model

import kotlinx.serialization.Serializable

/** 아기 동승 여행에서 필요한 시설 조건. */
@Serializable
enum class Amenity(val label: String) {
    NURSING_ROOM("수유실"),
    DIAPER_TABLE("기저귀 교환대"),
    HOT_WATER("온수"),
    PARKING("주차"),
}

/** 시설 정보의 출처. SAMPLE은 실제 운영 시설이 아닌 시연용 합성 데이터다. */
@Serializable
enum class DataOrigin(val label: String, val baseTrust: Double) {
    PUBLIC_DATA("공공데이터", 1.0),
    USER_CONFIRMED("사용자 확인", 0.9),
    SAMPLE("샘플 데이터", 0.6),
}

@Serializable
data class Place(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    /** 내비 POI 보조 ID. 내비가 알려준 목적지를 그대로 되돌려 보낼 때만 사용한다. */
    val poiSubId: String = "",
)

@Serializable
data class Facility(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    /** 시설 종류. 원본에 없으면 null (데이터 품질 지표에서 누락으로 센다). */
    val kind: String? = null,
    val amenities: Set<Amenity> = emptySet(),
    val origin: DataOrigin,
    /** 마지막 확인일 yyyy-MM-dd. */
    val verifiedOn: String,
) {
    fun toPlace() = Place(id = id, name = name, address = address, lat = lat, lon = lon)
}

@Serializable
enum class CarryItem(val label: String) {
    DIAPER("기저귀"),
    WIPES("물티슈"),
    FORMULA("분유"),
    BOTTLE("젖병"),
    BLANKET("담요"),
}

@Serializable
enum class TripState(val label: String) {
    PLANNING("계획"),
    DRIVING("이동"),
    STOPPED("휴식"),
    ARRIVED("도착"),
    CLOSED("종료"),
}

@Serializable
enum class CloseReason {
    COMPLETED,
    COMPLETED_NO_HANDOFF,
    CANCELLED_BEFORE_START,
    ROUTE_CANCELLED,
}

/** 이벤트가 어디서 왔는지. 자동(NAVI_SDK/REPLAY)과 수동(USER)을 구분한다. */
@Serializable
enum class EventSource(val automatic: Boolean) {
    NAVI_SDK(true),
    REPLAY(true),
    USER(false),
}

@Serializable
enum class NavMode(val label: String) {
    PLEOS("PLEOS"),
    REPLAY("REPLAY"),
}

/** 내비게이션에 경유지 등록을 요청한 결과 상태. SDK는 요청 후 확인 이벤트로만 성공을 알 수 있다. */
@Serializable
enum class Registration {
    NOT_REQUESTED,
    REQUESTED,
    CONFIRMED,
    FAILED,
    UNCONFIRMED,
}

@Serializable
data class PlannedStop(
    val facility: Facility,
    val registration: Registration = Registration.NOT_REQUESTED,
)

@Serializable
enum class CareKind(val label: String) {
    FEEDING("수유 완료"),
    DIAPER("기저귀 교환 완료"),
    FACILITY_OK("시설 이용 가능"),
    FACILITY_UNAVAILABLE("시설 이용 불가"),
}

/** 돌봄 기록. 아이 이름·사진·위치 이력은 저장하지 않는다. */
@Serializable
data class CareRecord(
    val id: String,
    val kind: CareKind,
    /** 기록 당시 정차 중이던 계획 경유지 인덱스. 알 수 없으면 null. */
    val stopIndex: Int?,
    val recordedAt: Long,
    val source: EventSource,
    val navMode: NavMode,
)

@Serializable
data class LogEntry(
    val at: Long,
    val event: String,
    val source: EventSource,
    val outcome: String,
)

@Serializable
data class TripSession(
    val id: String,
    val state: TripState,
    val destination: Place?,
    val restInMinutes: Int,
    val needs: Set<Amenity>,
    val carryItems: Set<CarryItem>,
    val plannedStops: List<PlannedStop> = emptyList(),
    val arrivedStopIndexes: Set<Int> = emptySet(),
    val currentStopIndex: Int? = null,
    val records: List<CareRecord> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val closeReason: CloseReason? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val arrivedAt: Long? = null,
    val navMode: NavMode,
)
