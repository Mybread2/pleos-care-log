import { MockVehicleDataSource } from '../mock/MockVehicleDataSource.js';
import { RecordEngine } from '../core/RecordEngine.js';
import { RecordStore } from '../core/RecordStore.js';
import { TEAM_ICE_FIXTURE_V02, TEAM_FIXTURE_SOURCE } from '../mock/teamFixture.js';

// UI coordination only. Sensor processing and correction contracts stay in F03.
export class DemoSession {
  constructor(onChange = () => {}, fixture = TEAM_ICE_FIXTURE_V02) {
    this.dataSource = new MockVehicleDataSource(fixture);
    this.source = fixture === TEAM_ICE_FIXTURE_V02 ? TEAM_FIXTURE_SOURCE : null;
    this.engineOptions = { powertrain: fixture.powertrain, samplingSec: fixture.mockFeed?.samplingSec };
    this.store = new RecordStore(RecordEngine.process([]));
    this.onChange = onChange;
    this.latestEvent = null;
    this.store.subscribe(() => this.onChange(this.snapshot()));
    this.dataSource.subscribe((event, events) => {
      this.latestEvent = event;
      this.store.updateEngineOutput(RecordEngine.process(events, fixture.vehicleId, this.engineOptions));
    });
  }

  snapshot() {
    const events = this.dataSource.getPublishedEvents();
    const latest = (signal) => events.findLast((event) => event.signal === signal);
    const ignition = latest('IGNITION')?.value ?? null;
    return {
      data: this.store.getData(), corrections: this.store.getCorrections(), events,
      source: this.source,
      latestEvent: this.latestEvent, ignition,
      // No speed/gear signal exists. OFF is the only allowed input mode.
      inputAllowed: ignition === 'OFF',
      step: this.dataSource.getCurrentStep(), total: this.dataSource.getTotalSteps(),
    };
  }

  next() { return this.dataSource.next(); }
  runAll() { return this.dataSource.emitAll(); }
  reset() {
    this.dataSource.reset();
    this.store.reset(RecordEngine.process([]));
  }

  correctAmount(input) {
    const state = this.snapshot();
    if (!state.data.energyEvents.length) return { ok: false, message: '아직 감지된 주유 기록이 없습니다. 전체 시연을 실행해 주세요.' };
    if (!state.inputAllowed) return { ok: false, message: '시동 OFF 후 결제금액을 입력할 수 있습니다.' };
    const text = String(input).trim();
    if (!/^\d{1,7}$/.test(text) || Number(text) <= 0) return { ok: false, message: '1원부터 9,999,999원까지 정수로 입력해 주세요.' };
    const amount = Number(text);
    const event = state.data.energyEvents[0];
    if (event.fields.amountKrw.value === amount) return { ok: false, message: '이미 같은 금액으로 저장되어 있습니다.' };
    const correction = this.store.updateEnergyField(event.id, 'amountKrw', amount, new Date().toISOString());
    return { ok: true, amount, correction, message: `${amount.toLocaleString('ko-KR')}원이 사용자 입력으로 저장되었습니다.` };
  }
}
