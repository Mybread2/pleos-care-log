import assert from 'node:assert/strict';
import { ICE_MOCK_FIXTURE_V02 } from '../src/mock/iceFixture.js';
import { MockVehicleDataSource } from '../src/mock/MockVehicleDataSource.js';
import { RecordEngine } from '../src/core/RecordEngine.js';
import { RecordStore } from '../src/core/RecordStore.js';

console.log('=== [F03] Mock Adapter & Contract Verification Start ===');

// 1. MockVehicleDataSource 단계별 방출 및 Fixture 정합성 검증
const dataSource = new MockVehicleDataSource(ICE_MOCK_FIXTURE_V02);
assert.equal(dataSource.getTotalSteps(), 11, 'Fixture must have 11 events');
assert.equal(dataSource.getCurrentStep(), 0, 'Initial step must be 0');

const firstEvent = dataSource.next();
assert.equal(firstEvent?.eventId, 'e01');
assert.equal(firstEvent?.signal, 'IGNITION');
assert.equal(firstEvent?.value, 'ON');
assert.equal(dataSource.getCurrentStep(), 1);

// 일괄 방출 검증
const allEmitted = dataSource.emitAll();
assert.equal(allEmitted.length, 11, 'All 11 events should be emitted');
console.log('✓ Step 1: MockVehicleDataSource step & emitAll passed');

// 2. RecordEngine 계약 및 기대값 검증 (B01-B 기준)
const engineOutput = RecordEngine.process(allEmitted);

// Leg 검증
assert.equal(engineOutput.legs.length, 2, 'Must produce exactly 2 legs');
const leg1 = engineOutput.legs[0];
assert.equal(leg1.id, 'leg-1');
assert.equal(leg1.distanceKm, 5, 'Leg 1 distance must be 5km (12340 -> 12345)');
assert.equal(leg1.activeDurationSec, 900, 'Leg 1 active duration must be 900s (15min)');

const leg2 = engineOutput.legs[1];
assert.equal(leg2.id, 'leg-2');
assert.equal(leg2.distanceKm, 7, 'Leg 2 distance must be 7km (12345 -> 12352)');
assert.equal(leg2.activeDurationSec, 1200, 'Leg 2 active duration must be 1200s (20min)');

// Journey 검증 (시동 OFF 10분 제외 유효 35분, 12km)
assert.equal(engineOutput.journeys.length, 1, 'Must group into 1 journey');
const journey = engineOutput.journeys[0];
assert.equal(journey.groupingBasis, 'MOCK_SCENARIO');
assert.equal(journey.distanceKm, 12, 'Total journey distance must be 12km');
assert.equal(journey.activeDurationSec, 2100, 'Total active duration must be 2100s (35min)');
console.log('✓ Step 2: RecordEngine Leg & Journey calculation passed');

// 주유 감지 검증
assert.equal(engineOutput.energyEvents.length, 1, 'Must detect 1 refuel event');
const refuel = engineOutput.energyEvents[0];
assert.equal(refuel.id, 'fuel-1');
assert.equal(refuel.kind, 'REFUEL');
assert.equal(refuel.detectedAt, '2026-11-15T08:25:00+09:00');
assert.equal(refuel.occurredAt, null, 'occurredAt must be null when unknown');
assert.equal(refuel.recordStatus, 'DETECTED_UNCONFIRMED');
assert.equal(refuel.fields.levelBeforePercent.value, 30);
assert.equal(refuel.fields.levelBeforePercent.provenance, 'OBSERVED');
assert.equal(refuel.fields.levelAfterPercent.value, 75);
assert.equal(refuel.fields.levelAfterPercent.provenance, 'OBSERVED');
assert.equal(refuel.fields.quantityLiters.value, null, 'Unknown quantity must be null, not 0');
assert.equal(refuel.fields.quantityLiters.provenance, 'UNKNOWN');
assert.equal(refuel.fields.amountKrw.value, null, 'Unknown amount must be null, not 0');
assert.equal(refuel.fields.amountKrw.provenance, 'UNKNOWN');
console.log('✓ Step 3: EnergyEvent (Refuel) detection & UNKNOWN fields passed');

// 3. RecordStore 및 정정(UserCorrection) 검증
const store = new RecordStore(engineOutput);
const correction = store.updateEnergyField('fuel-1', 'amountKrw', 65000);
assert.ok(correction, 'Correction should be created');
assert.equal(correction?.correctionId, 'correction-1');
assert.equal(correction?.previousField.value, null);
assert.equal(correction?.previousField.provenance, 'UNKNOWN');
assert.equal(correction?.newField.value, 65000);
assert.equal(correction?.newField.provenance, 'USER_INPUT');

// Store 상태 및 원본 보존 검증
const updatedFuel = store.getEnergyEvent('fuel-1');
assert.equal(updatedFuel?.fields.amountKrw.value, 65000);
assert.equal(updatedFuel?.fields.amountKrw.provenance, 'USER_INPUT');
assert.equal(updatedFuel?.recordStatus, 'DETECTED_UNCONFIRMED', 'recordStatus should remain DETECTED_UNCONFIRMED');

// 원시 Fixture 이벤트 불변성 확인
const rawFixture = dataSource.getRawFixtureEvents();
assert.equal(rawFixture.length, 11);
assert.equal(rawFixture[2].value, 30, 'Raw mock event value must remain unchanged');
console.log('✓ Step 4: RecordStore user correction & raw event immutability passed');

// 4. Demo Reset (DoD: 초기 상태 복원 및 재시연 검증)
dataSource.reset();
assert.equal(dataSource.getCurrentStep(), 0);
assert.equal(dataSource.getPublishedEvents().length, 0);

const freshEngineOutput = RecordEngine.process(dataSource.emitAll());
store.reset(freshEngineOutput);

assert.equal(store.getCorrections().length, 0, 'Corrections must be empty after reset');
const resetFuel = store.getEnergyEvent('fuel-1');
assert.equal(resetFuel?.fields.amountKrw.value, null, 'amountKrw must be reset to null');
assert.equal(resetFuel?.fields.amountKrw.provenance, 'UNKNOWN');
console.log('✓ Step 5: Demo Reset and reproducibility DoD passed');

console.log('\n🎉 ALL F03 CONTRACT & ADAPTER TESTS PASSED SUCCESSFULLY! 🎉');
