/**
 * AutoLog Mock Common Data Contract v0.2
 * Based on Notion Document: B01-A. Mock 공통 데이터 계약 v0.2
 */

export type Powertrain = 'ICE' | 'EV';

export type SignalType =
  | 'IGNITION'
  | 'READY'
  | 'ODOMETER_KM'
  | 'FUEL_PERCENT'
  | 'SOC_PERCENT';

export type IgnitionValue = 'ON' | 'OFF' | 'ACC';

export type Provenance = 'OBSERVED' | 'ESTIMATED' | 'USER_INPUT' | 'UNKNOWN';

export type RecordStatus =
  | 'DETECTED_UNCONFIRMED'
  | 'USER_CONFIRMED'
  | 'EXCLUDED_FALSE_POSITIVE';

export type GroupingBasis = 'MOCK_SCENARIO';

export interface MockEvent {
  eventId: string;
  at: string; // ISO 8601 (+09:00) - Mock sensor observation time
  signal: SignalType;
  value: string | number | boolean;
}

export interface Leg {
  id: string;
  startedAt: string;
  endedAt: string;
  startOdometerKm: number;
  endOdometerKm: number;
  activeDurationSec: number;
  distanceKm: number;
}

export interface Journey {
  id: string;
  legIds: string[];
  groupingBasis: GroupingBasis;
  activeDurationSec: number;
  distanceKm: number;
}

export interface ValueField<T> {
  value: T | null;
  provenance: Provenance;
}

export interface EnergyEventFields {
  levelBeforePercent: ValueField<number>;
  levelAfterPercent: ValueField<number>;
  quantityLiters?: ValueField<number>;
  /** B04 Mock EV extension proposal; not a finalized SDK field contract. */
  batteryAddedKwh?: ValueField<number>;
  billedKwh?: ValueField<number>;
  amountKrw: ValueField<number>;
}

export interface EnergyEvent {
  id: string;
  kind: 'REFUEL' | 'CHARGE';
  detectedAt: string;
  occurredAt: string | null;
  odometerKm: number | null;
  recordStatus: RecordStatus;
  fields: EnergyEventFields;
}

export interface UserCorrection {
  correctionId: string;
  targetEventId: string;
  field: keyof EnergyEventFields | string;
  previousField: ValueField<any>;
  newField: ValueField<any>;
  editedAt: string;
}

export interface MockFixtureData {
  schemaVersion: string;
  environment: 'MOCK';
  vehicleId: string;
  powertrain: Powertrain;
  events: MockEvent[];
}

export interface RecordEngineOutput {
  schemaVersion: string;
  environment: 'MOCK';
  vehicleId: string;
  legs: Leg[];
  journeys: Journey[];
  energyEvents: EnergyEvent[];
  /** B04 Mock extension, present only with an explicit sampling contract. */
  observationGaps?: Array<{ from: string; to: string; durationSec: number }>;
}
