import type { MockEvent, MockFixtureData } from '../types/models.ts';
import { ICE_MOCK_FIXTURE_V02 } from './iceFixture.ts';

export type EventCallback = (event: MockEvent, allCurrentEvents: MockEvent[]) => void;

/**
 * MockVehicleDataSource
 * F03 핵심 어댑터: 가상 차량 신호 스트림 공급 및 데모 단계 제어 (Next/Reset)
 */
export class MockVehicleDataSource {
  private fixture: MockFixtureData;
  private currentEventIndex: number = 0;
  private publishedEvents: MockEvent[] = [];
  private listeners: EventCallback[] = [];

  constructor(fixture: MockFixtureData = ICE_MOCK_FIXTURE_V02) {
    this.fixture = JSON.parse(JSON.stringify(fixture));
  }

  public subscribe(callback: EventCallback): () => void {
    this.listeners.push(callback);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== callback);
    };
  }

  private notify(event: MockEvent): void {
    this.listeners.forEach((listener) => listener(event, [...this.publishedEvents]));
  }

  /**
   * 다음 이벤트 1건 방출
   */
  public next(): MockEvent | null {
    if (this.currentEventIndex >= this.fixture.events.length) {
      return null;
    }
    const event = this.fixture.events[this.currentEventIndex];
    this.publishedEvents.push(event);
    this.currentEventIndex++;
    this.notify(event);
    return event;
  }

  /**
   * 특정 시점까지 일괄 방출 (또는 전체 시연 데이터 방출)
   */
  public emitAll(): MockEvent[] {
    while (this.currentEventIndex < this.fixture.events.length) {
      const event = this.fixture.events[this.currentEventIndex];
      this.publishedEvents.push(event);
      this.currentEventIndex++;
      this.notify(event);
    }
    return [...this.publishedEvents];
  }

  /**
   * 데모 리셋 (DoD: Demo Reset 시 초기 상태로 복구되어 동일 시연 재현 가능)
   */
  public reset(): void {
    this.currentEventIndex = 0;
    this.publishedEvents = [];
  }

  public getPublishedEvents(): MockEvent[] {
    return [...this.publishedEvents];
  }

  public getRawFixtureEvents(): MockEvent[] {
    return [...this.fixture.events];
  }

  public hasNext(): boolean {
    return this.currentEventIndex < this.fixture.events.length;
  }

  public getCurrentStep(): number {
    return this.currentEventIndex;
  }

  public getTotalSteps(): number {
    return this.fixture.events.length;
  }
}
