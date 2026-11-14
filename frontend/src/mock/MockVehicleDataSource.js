import { ICE_MOCK_FIXTURE_V02 } from './iceFixture.js';

/**
 * MockVehicleDataSource (ES Module)
 * F03 핵심 어댑터: 가상 차량 신호 스트림 공급 및 데모 단계 제어 (Next/Reset)
 */
export class MockVehicleDataSource {
  constructor(fixture = ICE_MOCK_FIXTURE_V02) {
    this.fixture = JSON.parse(JSON.stringify(fixture));
    this.currentEventIndex = 0;
    this.publishedEvents = [];
    this.listeners = [];
  }

  subscribe(callback) {
    this.listeners.push(callback);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== callback);
    };
  }

  notify(event) {
    this.listeners.forEach((listener) => listener(event, [...this.publishedEvents]));
  }

  next() {
    if (this.currentEventIndex >= this.fixture.events.length) {
      return null;
    }
    const event = this.fixture.events[this.currentEventIndex];
    this.publishedEvents.push(event);
    this.currentEventIndex++;
    this.notify(event);
    return event;
  }

  emitAll() {
    while (this.currentEventIndex < this.fixture.events.length) {
      const event = this.fixture.events[this.currentEventIndex];
      this.publishedEvents.push(event);
      this.currentEventIndex++;
      this.notify(event);
    }
    return [...this.publishedEvents];
  }

  reset() {
    this.currentEventIndex = 0;
    this.publishedEvents = [];
    this.notify(null);
  }

  getPublishedEvents() {
    return [...this.publishedEvents];
  }

  getRawFixtureEvents() {
    return [...this.fixture.events];
  }

  hasNext() {
    return this.currentEventIndex < this.fixture.events.length;
  }

  getCurrentStep() {
    return this.currentEventIndex;
  }

  getTotalSteps() {
    return this.fixture.events.length;
  }
}
