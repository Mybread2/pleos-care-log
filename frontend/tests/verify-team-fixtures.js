import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { RecordEngine } from '../src/core/RecordEngine.js';
import { RecordStore } from '../src/core/RecordStore.js';
import { ICE_MOCK_FIXTURE_V02 } from '../src/mock/iceFixture.js';
import { TEAM_ICE_FIXTURE_V02, TEAM_FIXTURE_SOURCE } from '../src/mock/teamFixture.js';
import { DemoSession } from '../src/ui/DemoSession.js';

const directory = new URL('./fixtures/team-b04/', import.meta.url);
const source = JSON.parse(fs.readFileSync(new URL('source.json', directory), 'utf8'));
const results = [];
let variants = 0;
for (const [file, digest] of Object.entries(source.files)) {
  const bytes = fs.readFileSync(new URL(file, directory));
  assert.equal(createHash('sha256').update(bytes).digest('hex'), digest, `${file}: pinned team source changed`);
  const fixture = JSON.parse(bytes.toString('utf8'));
  const original = JSON.stringify(fixture.events);
  const options = { powertrain: fixture.powertrain, samplingSec: fixture.mockFeed?.samplingSec };
  const actual = RecordEngine.process(fixture.events, fixture.vehicleId, options);
  assert.deepEqual(actual, fixture.expected, `${fixture.fixtureId}: output must match the team's full expected model`);

  const timestamps = [...new Set(fixture.events.map((event) => event.at))];
  const reordered = timestamps.flatMap((at) => fixture.events.filter((event) => event.at === at).reverse());
  for (let threshold = 3; threshold <= 44; threshold++) {
    for (const input of [fixture.events, reordered]) {
      assert.deepEqual(RecordEngine.process(input, fixture.vehicleId, { ...options, minimumEnergyIncreasePercent: threshold }), fixture.expected,
        `${fixture.fixtureId}: mock threshold ${threshold}, timestamp ordering invariance`);
      variants++;
    }
  }
  assert.deepEqual(RecordEngine.process([...fixture.events, ...fixture.events], fixture.vehicleId, options), actual, 'Full replay must be idempotent');
  assert.equal(JSON.stringify(fixture.events), original, 'Raw fixture must remain immutable');

  if (actual.energyEvents.length) {
    const store = new RecordStore(actual);
    store.updateEnergyField(actual.energyEvents[0].id, 'amountKrw', 65000);
    store.updateEngineOutput(RecordEngine.process(reordered, fixture.vehicleId, options));
    assert.equal(store.getData().energyEvents[0].fields.amountKrw.value, 65000, 'Recalculation must preserve correction');
    assert.equal(store.getData().energyEvents[0].recordStatus, 'DETECTED_UNCONFIRMED');
    assert.equal(store.getCorrections()[0].previousField.value, null);
    assert.equal(actual.energyEvents[0].fields.amountKrw.value, null, 'Original engine output must remain unchanged');
  }
  results.push({ fixture: fixture.fixtureId, pass: true, legs: actual.legs.length,
    distanceKm: actual.journeys[0]?.distanceKm, activeDurationSec: actual.journeys[0]?.activeDurationSec,
    energyEvents: actual.energyEvents.length, observationGaps: actual.observationGaps?.length ?? 0 });
}

// The production demo consumes the pinned source events, never the expected model.
assert.deepEqual(TEAM_ICE_FIXTURE_V02.events, ICE_MOCK_FIXTURE_V02.events);
assert.equal(Object.hasOwn(TEAM_ICE_FIXTURE_V02, 'expected'), false);
assert.equal(TEAM_FIXTURE_SOURCE.commit, source.commit);
const session = new DemoSession();
session.runAll();
assert.deepEqual(session.snapshot().data, JSON.parse(fs.readFileSync(new URL('F01_ice_basic_refuel.json', directory), 'utf8')).expected);
assert.equal(session.snapshot().source.fixtureId, 'F01');
session.correctAmount('65000');
assert.equal(session.snapshot().data.energyEvents[0].fields.amountKrw.value, 65000);
session.reset();
session.runAll();
assert.equal(session.snapshot().data.energyEvents[0].fields.amountKrw.value, null);

// An odometer reading of zero is valid; it must not be confused with UNKNOWN.
const zeroEvents = [
  { eventId: 'z1', at: '2026-11-15T08:00:00+09:00', signal: 'ODOMETER_KM', value: 0 },
  { eventId: 'z2', at: '2026-11-15T08:00:00+09:00', signal: 'IGNITION', value: 'ON' },
  { eventId: 'z3', at: '2026-11-15T08:05:00+09:00', signal: 'ODOMETER_KM', value: 5 },
  { eventId: 'z4', at: '2026-11-15T08:05:00+09:00', signal: 'IGNITION', value: 'OFF' },
];
assert.equal(RecordEngine.process(zeroEvents).legs[0].distanceKm, 5);
assert.throws(() => RecordEngine.process([...zeroEvents, { ...zeroEvents[0], value: 9 }]), /Conflicting/);
assert.throws(() => RecordEngine.process(zeroEvents, 'test', { samplingSec: 0 }), /Invalid mock sampling/);

const report = { verificationLevel: 'MOCK_ONLY', source, runtimeFixture: 'F01', results, thresholdAndOrderingVariants: variants,
  limitations: ['B04 assumptions are not adopted real-vehicle policies', 'EV processing is tested; the four-screen demo remains ICE F01', 'No remote API, Android APK, persistence or real-vehicle verification is claimed'] };
fs.mkdirSync(new URL('../artifacts/', import.meta.url), { recursive: true });
fs.writeFileSync(new URL('../artifacts/team-fixture-audit.after.json', import.meta.url), `${JSON.stringify(report, null, 2)}\n`);
console.log(`✓ Team B04: ${results.length}/${results.length} fixtures passed; ${variants} threshold/ordering variants passed; replay, correction preservation and source integrity passed.`);
