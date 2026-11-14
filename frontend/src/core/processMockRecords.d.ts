import type { MockEvent, Powertrain, RecordEngineOutput } from '../types/models.ts';

export interface MockRecordOptions {
  powertrain?: Powertrain;
  /** B04 test assumption only; not a validated vehicle detection threshold. */
  minimumEnergyIncreasePercent?: number;
  /** Explicit mock feed interval. Omit when no sampling contract exists. */
  samplingSec?: number;
}

export function processMockRecords(
  events: MockEvent[],
  vehicleId: string,
  options?: MockRecordOptions,
): RecordEngineOutput;
