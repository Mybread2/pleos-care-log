/**
 * ICE 원시 이벤트 Fixture v0.2
 * Reference: Notion B01-A. Mock 공통 데이터 계약 v0.2 Section 4
 */
export const ICE_MOCK_FIXTURE_V02 = {
  schemaVersion: '0.2',
  environment: 'MOCK',
  vehicleId: 'demo-ice-01',
  powertrain: 'ICE',
  events: [
    {
      eventId: 'e01',
      at: '2026-11-15T08:00:00+09:00',
      signal: 'IGNITION',
      value: 'ON',
    },
    {
      eventId: 'e02',
      at: '2026-11-15T08:00:00+09:00',
      signal: 'ODOMETER_KM',
      value: 12340,
    },
    {
      eventId: 'e03',
      at: '2026-11-15T08:00:00+09:00',
      signal: 'FUEL_PERCENT',
      value: 30,
    },
    {
      eventId: 'e04',
      at: '2026-11-15T08:15:00+09:00',
      signal: 'ODOMETER_KM',
      value: 12345,
    },
    {
      eventId: 'e05',
      at: '2026-11-15T08:15:00+09:00',
      signal: 'FUEL_PERCENT',
      value: 30,
    },
    {
      eventId: 'e06',
      at: '2026-11-15T08:15:00+09:00',
      signal: 'IGNITION',
      value: 'OFF',
    },
    {
      eventId: 'e07',
      at: '2026-11-15T08:25:00+09:00',
      signal: 'FUEL_PERCENT',
      value: 75,
    },
    {
      eventId: 'e08',
      at: '2026-11-15T08:25:00+09:00',
      signal: 'IGNITION',
      value: 'ON',
    },
    {
      eventId: 'e09',
      at: '2026-11-15T08:25:00+09:00',
      signal: 'ODOMETER_KM',
      value: 12345,
    },
    {
      eventId: 'e10',
      at: '2026-11-15T08:45:00+09:00',
      signal: 'ODOMETER_KM',
      value: 12352,
    },
    {
      eventId: 'e11',
      at: '2026-11-15T08:45:00+09:00',
      signal: 'IGNITION',
      value: 'OFF',
    },
  ],
};
