package com.carelog.app.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.OffsetDateTime

class RecordEngineTest {
    private val gson = GsonBuilder().serializeNulls().create()

    @Test fun `F01 ICE 주행과 미확인 주유 기록`() = assertFixture("F01_ice_basic_refuel")

    @Test fun `F02 EV 주행과 미확인 충전 기록`() = assertFixture("F02_ev_ready_charge")

    @Test fun `F03 주행 중 Fuel 증가와 작은 정차 변동은 주유가 아님`() =
        assertFixture("F03_ice_fuel_rise_not_refuel")

    @Test fun `F04는 공백 600초를 빼고 하나의 Leg와 누적계 차분을 유지`() =
        assertFixture("F04_ice_signal_gap")

    @Test fun `F05 재전송과 반복 ON에도 F01과 동일`() {
        val (input, expected) = fixture("F05_ice_duplicate_input")
        val baseline = RecordEngine.process(fixture("F01_ice_basic_refuel").first)
        val replay = RecordEngine.process(input.copy(events = input.events + input.events))
        assertEquals(baseline.records, replay.records)
        assertEquals(expected, replay.records.json())
    }

    @Test fun `F06 작은 SOC 증가는 충전이 아님`() = assertFixture("F06_ev_short_stop_no_charge")

    @Test fun `운행 중 P나 ISG처럼 ON 상태가 유지되면 Leg를 닫지 않음`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val stationary = MockEvent(
            "stationary", OffsetDateTime.parse("2026-11-15T08:05:00+09:00"),
            MockSignal.ODOMETER_KM, 12340.0,
        )
        val stillOn = stationary.copy(eventId = "still-on", signal = MockSignal.IGNITION, value = "ON")
        val legs = RecordEngine.process(input.copy(events = input.events + stationary + stillOn)).records.legs
        assertEquals(listOf(900L, 1200L), legs.map { it.activeDurationSec })
    }

    @Test fun `Journey 경계를 바꿔도 원본 Leg는 그대로`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val recording = RecordEngine.process(input)
        val views = recording.withJourneyBoundaries(setOf("leg-1")).records
        assertEquals(recording.records.legs, views.legs)
        assertEquals(listOf(listOf("leg-1"), listOf("leg-2")), views.journeys.map { it.legIds })
        assertEquals(listOf(900L, 1200L), views.journeys.map { it.activeDurationSec })
        assertEquals(listOf(5.0, 7.0), views.journeys.map { it.distanceKm })
    }

    @Test fun `ACC는 운행에서 제외하고 주유 후보에는 실제 OFF 관측이 필요`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val accInsteadOfOff = input.events.map { if (it.eventId == "e06") it.copy(value = "ACC") else it }
        val accRecords = RecordEngine.process(input.copy(events = accInsteadOfOff)).records
        assertEquals(2100L, accRecords.journeys.single().activeDurationSec)
        assertEquals(emptyList<EnergyEvent>(), accRecords.energyEvents)

        val realOff = input.events.first { it.eventId == "e06" }.copy(
            eventId = "off-after-acc",
            at = OffsetDateTime.parse("2026-11-15T08:16:00+09:00"),
        )
        val fuelAtOff = realOff.copy(eventId = "fuel-at-off", signal = MockSignal.FUEL_PERCENT, value = 30.0)
        val offRecords = RecordEngine.process(input.copy(events = accInsteadOfOff + realOff + fuelAtOff)).records
        assertEquals(2100L, offRecords.journeys.single().activeDurationSec)
        assertEquals(EnergyKind.REFUEL, offRecords.energyEvents.single().kind)
    }

    @Test fun `금액 정정은 원본과 이전 값 이력을 유지하고 재처리 후에도 읽을 수 있음`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val recording = RecordEngine.process(input)
        val editedAt = OffsetDateTime.parse("2026-11-15T08:46:00+09:00")
        val edited = recording.correctAmount("fuel-1", 65_000, editedAt)
        val savedCorrections = edited.correctAmount("fuel-1", 67_000, editedAt.plusMinutes(1)).corrections
        val replay = RecordEngine.process(input, savedCorrections)

        val unknown = ValueField(null, Provenance.UNKNOWN)
        val entered = ValueField(65_000L, Provenance.USER_INPUT)
        val original = recording.records.energyEvents.single()
        assertEquals(unknown, original.fields.getValue("amountKrw"))
        assertEquals(entered, edited.records.energyEvents.single().fields.getValue("amountKrw"))
        assertEquals(unknown, savedCorrections.first().previousField)
        assertEquals(entered, savedCorrections.last().previousField)
        assertEquals(editedAt, savedCorrections.first().editedAt)
        assertEquals(input.events, replay.rawEvents)
        assertEquals(
            original.copy(fields = original.fields + ("amountKrw" to ValueField(67_000L, Provenance.USER_INPUT))),
            replay.records.energyEvents.single(),
        )
        assertEquals(savedCorrections, replay.corrections)
    }

    @Test fun `거리 증거 없음은 영으로 채우지 않음`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val withoutOdometer = input.copy(events = input.events.filter { it.signal != MockSignal.ODOMETER_KM })
        val result = RecordEngine.process(withoutOdometer).records
        result.legs.forEach { assertNull(it.distanceKm) }
        assertNull(result.journeys.single().distanceKm)
        assertNull(result.energyEvents.single().odometerKm)
    }

    @Test fun `이전 관측을 누락된 종료 거리나 OFF 시점 연료로 사용하지 않음`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val incomplete = input.copy(events = input.events.filterNot { it.eventId in setOf("e04", "e05") })
        val result = RecordEngine.process(incomplete).records
        assertNull(result.legs.first().endOdometerKm)
        assertNull(result.legs.first().distanceKm)
        assertEquals(emptyList<EnergyEvent>(), result.energyEvents)
    }

    @Test fun `같은 이벤트 ID의 다른 페이로드는 입력 오류`() {
        val (input) = fixture("F01_ice_basic_refuel")
        val conflicting = input.events.first().copy(value = "OFF")
        assertThrows(IllegalArgumentException::class.java) {
            RecordEngine.process(input.copy(events = input.events + conflicting))
        }
    }

    private fun assertFixture(name: String) {
        val (input, expected) = fixture(name)
        assertEquals(expected, RecordEngine.process(input).records.json())
        assertEquals(expected, RecordEngine.process(input.copy(events = input.events.reversed())).records.json())
    }

    private fun RecordOutput.json(): JsonObject = gson.toJsonTree(this).asJsonObject.apply {
        if (observationGaps == null) remove("observationGaps")
    }

    private fun fixture(name: String): Pair<MockInput, JsonObject> {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/$name.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val input = MockInput(
            schemaVersion = fixture["schemaVersion"].asString,
            environment = fixture["environment"].asString,
            vehicleId = fixture["vehicleId"].asString,
            powertrain = Powertrain.valueOf(fixture["powertrain"].asString),
            mockFeed = fixture["mockFeed"]?.asJsonObject?.get("samplingSec")?.asLong?.let(::MockFeed),
            events = fixture["events"].asJsonArray.map { element ->
                val event = element.asJsonObject
                val value = event["value"]
                MockEvent(
                    event["eventId"].asString,
                    OffsetDateTime.parse(event["at"].asString),
                    MockSignal.valueOf(event["signal"].asString),
                    when {
                        value.isJsonNull -> null
                        value.asJsonPrimitive.isBoolean -> value.asBoolean
                        value.asJsonPrimitive.isNumber -> value.asDouble
                        else -> value.asString
                    },
                )
            },
        )
        return input to fixture["expected"].asJsonObject
    }
}
