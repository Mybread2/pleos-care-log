// B04 assumptions are for synthetic fixtures only. This is not a Pleos SDK
// adapter or an adopted real-vehicle refuelling/gap detection policy.
const mockField = (value, provenance) => ({ value, provenance });
const unknownMockField = () => mockField(null, 'UNKNOWN');

export function processMockRecords(events, vehicleId, options = {}) {
  const isEv = options.powertrain === 'EV' || (!options.powertrain && events.some((event) => event.signal === 'READY'));
  const powerSignal = isEv ? 'READY' : 'IGNITION';
  const levelSignal = isEv ? 'SOC_PERCENT' : 'FUEL_PERCENT';
  // B04's expected results are invariant across the entire 3–44%p range.
  // The default is the lower end of that test range, not a product decision.
  const minimumIncrease = options.minimumEnergyIncreasePercent ?? 3;
  const samplingSec = options.samplingSec;
  if (!Number.isFinite(minimumIncrease) || minimumIncrease <= 0) throw new Error('Invalid mock energy increase threshold');
  if (samplingSec !== undefined && (!Number.isFinite(samplingSec) || samplingSec <= 0)) throw new Error('Invalid mock sampling interval');

  const ids = new Map();
  const observations = new Set();
  const unique = [];
  for (const event of events) {
    if (!Number.isFinite(Date.parse(event.at))) throw new Error('Invalid mock observation time');
    const observation = JSON.stringify([event.signal, event.at, event.value]);
    if (ids.has(event.eventId) && ids.get(event.eventId) !== observation) {
      throw new Error(`Conflicting mock event ID: ${event.eventId}`);
    }
    ids.set(event.eventId, observation);
    if (observations.has(observation)) continue;
    observations.add(observation);
    unique.push(event);
  }
  // Process a timestamp's readings before its power transition. Never reorder
  // or delete raw source events; only this derived working list is sorted.
  unique.sort((left, right) => Date.parse(left.at) - Date.parse(right.at)
    || Number(left.signal === powerSignal) - Number(right.signal === powerSignal));

  const legs = [];
  const energyEvents = [];
  const observationGaps = [];
  let currentLeg = null;
  let lastOdometer = null;
  let lastLevel = null;
  let stoppedLevel = null;
  let powerState = null;
  let previousObservation = null;

  for (const event of unique) {
    if (currentLeg && samplingSec !== undefined && previousObservation) {
      const durationSec = (Date.parse(event.at) - Date.parse(previousObservation.at)) / 1000;
      if (durationSec > samplingSec) {
        observationGaps.push({ from: previousObservation.at, to: event.at, durationSec });
        currentLeg.gapSeconds += durationSec;
      }
    }
    previousObservation = event;

    if (event.signal === 'ODOMETER_KM' && typeof event.value === 'number' && Number.isFinite(event.value) && event.value >= 0) {
      lastOdometer = event.value;
      if (currentLeg && currentLeg.startOdometer === null) currentLeg.startOdometer = lastOdometer;
    }
    if (event.signal === levelSignal && typeof event.value === 'number' && Number.isFinite(event.value) && event.value >= 0 && event.value <= 100) {
      lastLevel = event.value;
      if (powerState === false && stoppedLevel === null) stoppedLevel = lastLevel;
    }
    if (event.signal !== powerSignal) continue;
    const isOn = isEv ? event.value === true : event.value === 'ON';
    const isOff = isEv ? event.value === false : event.value === 'OFF';
    // ACC, parking, and stop/start data are not silently treated as power OFF.
    if (!isOn && !isOff) continue;
    if (powerState === isOn) continue;
    powerState = isOn;

    if (isOn) {
      currentLeg = { startedAt: event.at, startOdometer: lastOdometer, gapSeconds: 0 };
      if (stoppedLevel !== null && lastLevel !== null && lastLevel - stoppedLevel >= minimumIncrease) {
        const fields = {
          levelBeforePercent: mockField(stoppedLevel, 'OBSERVED'),
          levelAfterPercent: mockField(lastLevel, 'OBSERVED'),
          ...(isEv
            ? { batteryAddedKwh: unknownMockField(), billedKwh: unknownMockField() }
            : { quantityLiters: unknownMockField() }),
          amountKrw: unknownMockField(),
        };
        energyEvents.push({
          id: `${isEv ? 'charge' : 'fuel'}-${energyEvents.length + 1}`,
          kind: isEv ? 'CHARGE' : 'REFUEL',
          detectedAt: event.at,
          occurredAt: null,
          odometerKm: lastOdometer,
          recordStatus: 'DETECTED_UNCONFIRMED',
          fields,
        });
      }
      stoppedLevel = null;
    } else {
      if (currentLeg && currentLeg.startOdometer !== null && lastOdometer !== null) {
        legs.push({
          id: `leg-${legs.length + 1}`,
          startedAt: currentLeg.startedAt,
          endedAt: event.at,
          startOdometerKm: currentLeg.startOdometer,
          endOdometerKm: lastOdometer,
          activeDurationSec: Math.max(0, Math.round((Date.parse(event.at) - Date.parse(currentLeg.startedAt)) / 1000) - currentLeg.gapSeconds),
          distanceKm: Math.max(0, lastOdometer - currentLeg.startOdometer),
        });
      }
      currentLeg = null;
      stoppedLevel = lastLevel;
    }
  }

  const journeys = legs.length ? [{
    id: 'journey-1', legIds: legs.map((leg) => leg.id), groupingBasis: 'MOCK_SCENARIO',
    activeDurationSec: legs.reduce((sum, leg) => sum + leg.activeDurationSec, 0),
    distanceKm: legs.reduce((sum, leg) => sum + leg.distanceKm, 0),
  }] : [];
  return {
    schemaVersion: '0.2', environment: 'MOCK', vehicleId, legs, journeys, energyEvents,
    ...(samplingSec !== undefined ? { observationGaps } : {}),
  };
}
