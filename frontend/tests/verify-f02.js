import assert from 'node:assert/strict';
import { DemoSession } from '../src/ui/DemoSession.js';

const notifications = [];
const session = new DemoSession((state) => notifications.push(state.step));
const originalFixture = JSON.stringify(session.dataSource.getRawFixtureEvents());
assert.equal(session.snapshot().inputAllowed, false, 'No signal must not imply safe input');
assert.equal(session.correctAmount('65000').ok, false, 'No correction before detection');

// Exercise actual UI controller and F03 integration through every fixture event.
for (let step = 1; step <= 8; step++) {
  session.next();
  assert.equal(session.snapshot().step, step);
}
let state = session.snapshot();
assert.equal(state.data.journeys[0].distanceKm, 5, 'Partial summary must only include completed legs');
assert.equal(state.data.energyEvents.length, 1);
assert.equal(state.ignition, 'ON');
assert.equal(state.inputAllowed, false);
assert.equal(session.correctAmount('65000').ok, false, 'Detected event must still be locked while ignition ON');
assert.equal(session.store.getCorrections().length, 0);

session.runAll();
state = session.snapshot();
assert.equal(state.step, 11);
assert.equal(state.inputAllowed, true);
assert.equal(state.data.journeys[0].distanceKm, 12);
assert.equal(state.data.journeys[0].activeDurationSec, 2100);
for (const invalid of ['', '0', '-1', '65000abc', '65000.5', '1e5', '10000000', 'NaN']) {
  assert.equal(session.correctAmount(invalid).ok, false, `Reject invalid amount: ${invalid}`);
}
assert.equal(session.store.getCorrections().length, 0);
assert.equal(session.correctAmount('65000').ok, true);
assert.equal(session.store.getEnergyEvent('fuel-1').fields.amountKrw.provenance, 'USER_INPUT');
assert.equal(session.store.getCorrections()[0].previousField.value, null);
assert.equal(session.correctAmount('65000').ok, false, 'Duplicate submissions must not add history');
assert.equal(session.correctAmount('70000').ok, true);
assert.equal(session.store.getCorrections().length, 2);
assert.equal(session.store.getCorrections()[1].previousField.value, 65000);
assert.equal(session.store.getEnergyEvent('fuel-1').fields.quantityLiters.value, null);
assert.equal(session.store.getEnergyEvent('fuel-1').recordStatus, 'DETECTED_UNCONFIRMED');
assert.equal(JSON.stringify(session.dataSource.getRawFixtureEvents()), originalFixture);
session.runAll();
assert.equal(session.store.getCorrections().length, 2, 'Run-all at the end must preserve corrections');
assert.equal(session.next(), null);

session.reset();
state = session.snapshot();
assert.equal(state.step, 0);
assert.equal(state.latestEvent, null);
assert.equal(state.ignition, null);
assert.equal(state.inputAllowed, false);
assert.equal(state.events.length, 0);
assert.equal(state.data.journeys.length, 0);
assert.equal(state.corrections.length, 0);
session.runAll();
assert.equal(session.store.getEnergyEvent('fuel-1').fields.amountKrw.value, null);
assert.equal(session.correctAmount('50000').correction.correctionId, 'correction-1');
assert.ok(notifications.includes(0) && notifications.includes(11), 'UI must be notified on reset and completion');
console.log('✓ F02 integration: partial steps, input safety, validation, correction history, original preservation, reset and replay passed.');
