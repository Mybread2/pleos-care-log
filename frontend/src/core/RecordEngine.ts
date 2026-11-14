import type { MockEvent, RecordEngineOutput } from '../types/models.ts';
import { processMockRecords } from './processMockRecords.js';
import type { MockRecordOptions } from './processMockRecords.js';

/** Typed entry point for the same implementation used by the browser. */
export class RecordEngine {
  public static process(
    events: MockEvent[],
    vehicleId: string = 'demo-ice-01',
    options: MockRecordOptions = {},
  ): RecordEngineOutput {
    return processMockRecords(events, vehicleId, options);
  }
}
