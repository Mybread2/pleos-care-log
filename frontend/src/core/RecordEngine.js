import { processMockRecords } from './processMockRecords.js';

/** B01 output contract, with B04's explicitly mock-only test assumptions. */
export class RecordEngine {
  static process(events, vehicleId = 'demo-ice-01', options = {}) {
    return processMockRecords(events, vehicleId, options);
  }
}
