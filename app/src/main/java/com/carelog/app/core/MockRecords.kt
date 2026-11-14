package com.carelog.app.core

import java.time.OffsetDateTime

enum class Powertrain { ICE, EV }
enum class MockSignal { IGNITION, READY, ODOMETER_KM, FUEL_PERCENT, SOC_PERCENT }
enum class Provenance { OBSERVED, ESTIMATED, USER_INPUT, UNKNOWN }
enum class EnergyKind { REFUEL, CHARGE }
enum class RecordStatus { DETECTED_UNCONFIRMED, USER_CONFIRMED, EXCLUDED_FALSE_POSITIVE }
enum class GroupingBasis { MOCK_SCENARIO, USER_EDITED }

data class MockInput(
    val vehicleId: String,
    val powertrain: Powertrain,
    val events: List<MockEvent>,
    val mockFeed: MockFeed? = null,
    val schemaVersion: String = "0.2",
    val environment: String = "MOCK",
)

data class MockFeed(val samplingSec: Long)

/** Raw B01 event. The signal determines whether the value is a string, boolean or nullable number. */
data class MockEvent(
    val eventId: String,
    val at: OffsetDateTime,
    val signal: MockSignal,
    val value: Any?,
)

data class Leg(
    val id: String,
    val startedAt: String,
    val endedAt: String,
    val startOdometerKm: Double?,
    val endOdometerKm: Double?,
    val activeDurationSec: Long,
    val distanceKm: Double?,
)

data class Journey(
    val id: String,
    val legIds: List<String>,
    val groupingBasis: GroupingBasis,
    val activeDurationSec: Long,
    val distanceKm: Double?,
)

data class ObservationGap(val from: String, val to: String, val durationSec: Long)

data class ValueField(val value: Number?, val provenance: Provenance)

data class EnergyEvent(
    val id: String,
    val kind: EnergyKind,
    val detectedAt: String,
    val occurredAt: String?,
    val odometerKm: Double?,
    val recordStatus: RecordStatus,
    val fields: Map<String, ValueField>,
)

data class UserCorrection(
    val correctionId: String,
    val targetEventId: String,
    val field: String,
    val previousField: ValueField,
    val newField: ValueField,
    val editedAt: OffsetDateTime,
)

data class RecordOutput(
    val schemaVersion: String,
    val environment: String,
    val vehicleId: String,
    val legs: List<Leg>,
    val journeys: List<Journey>,
    val energyEvents: List<EnergyEvent>,
    val observationGaps: List<ObservationGap>?,
)

private const val AMOUNT_KRW = "amountKrw"

/** Corrections are replayed over the same Mock scenario and can be saved by the caller. */
class MockRecording internal constructor(
    val rawEvents: List<MockEvent>,
    private val original: RecordOutput,
    val corrections: List<UserCorrection>,
    private val journeyBoundaries: Set<String>? = null,
) {
    val records: RecordOutput
        get() = original.copy(
            journeys = journeyBoundaries?.let { journeysFor(original.legs, it) } ?: original.journeys,
            energyEvents = original.energyEvents.map { event ->
                val correction = corrections.lastOrNull { it.targetEventId == event.id && it.field == AMOUNT_KRW }
                if (correction == null) event else event.copy(fields = event.fields + (AMOUNT_KRW to correction.newField))
            },
        )

    fun correctAmount(eventId: String, amountKrw: Long, editedAt: OffsetDateTime): MockRecording {
        require(amountKrw >= 0) { "amountKrw must be non-negative" }
        val event = requireNotNull(records.energyEvents.find { it.id == eventId }) { "Unknown energy event: $eventId" }
        val correction = UserCorrection(
            correctionId = "correction-${corrections.size + 1}",
            targetEventId = eventId,
            field = AMOUNT_KRW,
            previousField = event.fields.getValue(AMOUNT_KRW),
            newField = ValueField(amountKrw, Provenance.USER_INPUT),
            editedAt = editedAt,
        )
        return MockRecording(rawEvents, original, corrections + correction, journeyBoundaries)
    }

    /** Each boundary is the ID of the Leg directly before the split. */
    fun withJourneyBoundaries(boundariesAfter: Set<String>): MockRecording {
        val validBoundaries = original.legs.dropLast(1).map { it.id }.toSet()
        require(validBoundaries.containsAll(boundariesAfter)) { "Journey boundaries must fall between adjacent Legs" }
        return MockRecording(rawEvents, original, corrections, boundariesAfter.toSet())
    }
}

internal fun journeysFor(legs: List<Leg>, boundariesAfter: Set<String>? = null): List<Journey> {
    val groups = mutableListOf<List<Leg>>()
    var start = 0
    for ((index, leg) in legs.withIndex()) {
        if (index == legs.lastIndex || boundariesAfter?.contains(leg.id) == true) {
            groups += legs.subList(start, index + 1)
            start = index + 1
        }
    }
    return groups.mapIndexed { index, group ->
        Journey(
            id = "journey-${index + 1}",
            legIds = group.map { it.id },
            groupingBasis = if (boundariesAfter == null) GroupingBasis.MOCK_SCENARIO else GroupingBasis.USER_EDITED,
            activeDurationSec = group.sumOf { it.activeDurationSec },
            distanceKm = group.mapNotNull { it.distanceKm }.takeIf { it.isNotEmpty() }?.sum(),
        )
    }
}
