package com.carelog.app.core

import com.carelog.app.core.MockSignal.FUEL_PERCENT
import com.carelog.app.core.MockSignal.IGNITION
import com.carelog.app.core.MockSignal.ODOMETER_KM
import com.carelog.app.core.MockSignal.READY
import com.carelog.app.core.MockSignal.SOC_PERCENT
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

/** B05 batch processor for one complete B01 v0.2 Mock scenario. */
object RecordEngine {
    private data class OpenLeg(val at: OffsetDateTime, val odometerKm: Double?)
    private data class Observation(val at: OffsetDateTime, val signal: MockSignal, val value: Any?)

    fun process(input: MockInput, corrections: List<UserCorrection> = emptyList()): MockRecording {
        require(input.schemaVersion == "0.2" && input.environment == "MOCK" && input.vehicleId.isNotBlank())
        val samplingSec = input.mockFeed?.samplingSec
        require(samplingSec == null || samplingSec > 0) { "samplingSec must be positive" }

        val isIce = input.powertrain == Powertrain.ICE
        val powerSignal = if (isIce) IGNITION else READY
        val levelSignal = if (isIce) FUEL_PERCENT else SOC_PERCENT
        val seenIds = mutableMapOf<String, Observation>()
        val observations = mutableSetOf<Observation>()
        for (event in input.events) {
            require(event.eventId.isNotBlank() && event.at.offset == ZoneOffset.ofHours(9))
            require(event.signal in setOf(powerSignal, levelSignal, ODOMETER_KM)) { "Unexpected signal: ${event.signal}" }
            val observation = Observation(event.at, event.signal, validatedValue(event))
            val existing = seenIds.putIfAbsent(event.eventId, observation)
            require(existing == null || existing == observation) { "Conflicting event ID: ${event.eventId}" }
            observations += observation
        }

        var openLeg: OpenLeg? = null
        var previousAt: OffsetDateTime? = null
        var levelAtOff: Double? = null
        var offObserved = false
        var gapDuration = Duration.ZERO
        val legs = mutableListOf<Leg>()
        val gaps = mutableListOf<ObservationGap>()
        val energyEvents = mutableListOf<EnergyEvent>()

        for ((at, group) in observations.groupBy { it.at }.toSortedMap()) {
            val readings = group.associateBy { it.signal }
            require(readings.size == group.size) { "Conflicting observations at $at" }
            val atText = at.format(ISO_OFFSET_DATE_TIME)
            // shortcut: Mock snapshots need same-time evidence; use freshness rules when the real SDK is validated.
            val odometerKm = readings[ODOMETER_KM]?.value as Double?
            val levelReading = readings[levelSignal]
            val level = levelReading?.value as Double?

            val leg = openLeg
            val previous = previousAt
            if (leg != null && samplingSec != null && previous != null) {
                val interval = Duration.between(previous, at)
                // shortcut: B04 keeps the Mock Leg open across a gap, revisit when power continuity can be verified.
                if (interval > Duration.ofSeconds(samplingSec)) {
                    gaps += ObservationGap(previous.format(ISO_OFFSET_DATE_TIME), atText, interval.seconds)
                    gapDuration = gapDuration.plus(interval)
                }
            }

            val power = readings[powerSignal]
            if (power != null) {
                val powered = if (isIce) power.value == "ON" else power.value == true
                val off = if (isIce) power.value == "OFF" else power.value == false
                if (off && (!offObserved || levelAtOff == null)) {
                    levelAtOff = level
                    offObserved = true
                }
                if (powered && leg == null) {
                    offObserved = false
                    openLeg = OpenLeg(at, odometerKm)
                    gapDuration = Duration.ZERO
                } else if (!powered && leg != null) {
                    val distanceKm = if (leg.odometerKm != null && odometerKm != null && odometerKm >= leg.odometerKm) {
                        odometerKm - leg.odometerKm
                    } else {
                        null
                    }
                    legs += Leg(
                        id = "leg-${legs.size + 1}",
                        startedAt = leg.at.format(ISO_OFFSET_DATE_TIME),
                        endedAt = atText,
                        startOdometerKm = leg.odometerKm,
                        endOdometerKm = odometerKm,
                        activeDurationSec = Duration.between(leg.at, at).minus(gapDuration).seconds,
                        distanceKm = distanceKm,
                    )
                    openLeg = null
                    if (!off) levelAtOff = null
                }
            }

            val before = levelAtOff
            if (openLeg != null && before != null && levelReading != null) {
                val after = level
                val minimumDelta = if (isIce) RefillDetector.FUEL_MIN_DELTA else RefillDetector.SOC_MIN_DELTA
                if (after != null && after - before >= minimumDelta) {
                    energyEvents += energyEvent(isIce, energyEvents.size + 1, atText, odometerKm, before, after)
                }
                levelAtOff = null
            }
            previousAt = at
        }

        val output = RecordOutput(
            schemaVersion = input.schemaVersion,
            environment = input.environment,
            vehicleId = input.vehicleId,
            legs = legs.toList(),
            journeys = journeysFor(legs),
            energyEvents = energyEvents.toList(),
            observationGaps = samplingSec?.let { gaps.toList() },
        )
        return MockRecording(input.events.toList(), output, corrections.toList())
    }

    private fun validatedValue(event: MockEvent): Any? = when (event.signal) {
        IGNITION -> event.value.also { require(it == "ON" || it == "OFF" || it == "ACC") }
        READY -> event.value.also { require(it is Boolean) }
        ODOMETER_KM, FUEL_PERCENT, SOC_PERCENT -> event.value?.let {
            require(it is Number)
            val value = it.toDouble()
            require(value.isFinite() && value >= 0 && (event.signal == ODOMETER_KM || value <= 100))
            value
        }
    }

    private fun energyEvent(
        isIce: Boolean,
        number: Int,
        at: String,
        odometerKm: Double?,
        before: Double,
        after: Double,
    ): EnergyEvent {
        val unknown = ValueField(null, Provenance.UNKNOWN)
        val fields = mutableMapOf(
            "levelBeforePercent" to ValueField(before, Provenance.OBSERVED),
            "levelAfterPercent" to ValueField(after, Provenance.OBSERVED),
            "amountKrw" to unknown,
        )
        if (isIce) {
            fields["quantityLiters"] = unknown
        } else {
            fields["batteryAddedKwh"] = unknown
            fields["billedKwh"] = unknown
        }
        return EnergyEvent(
            id = if (isIce) "fuel-$number" else "charge-$number",
            kind = if (isIce) EnergyKind.REFUEL else EnergyKind.CHARGE,
            detectedAt = at,
            occurredAt = null,
            odometerKm = odometerKm,
            recordStatus = RecordStatus.DETECTED_UNCONFIRMED,
            fields = fields.toMap(),
        )
    }
}
