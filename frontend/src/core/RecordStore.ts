import type {
  RecordEngineOutput,
  UserCorrection,
  ValueField,
  EnergyEvent,
  EnergyEventFields,
} from '../types/models.ts';

export type StoreListener = (store: RecordStore) => void;

/**
 * RecordStore
 * F03 핵심 저장소: 엔진 산출물 관리, 사용자 정정(UserCorrection) 기록 및 원본 불변성 보장
 */
export class RecordStore {
  private currentData: RecordEngineOutput;
  private corrections: UserCorrection[] = [];
  private listeners: StoreListener[] = [];
  private correctionCounter = 1;

  constructor(initialData: RecordEngineOutput) {
    this.currentData = JSON.parse(JSON.stringify(initialData));
  }

  public subscribe(listener: StoreListener): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  private notify(): void {
    this.listeners.forEach((l) => l(this));
  }

  public updateEngineOutput(output: RecordEngineOutput): void {
    // 새 엔진 출력이 들어오면 기존 UserCorrection들을 재적용하여 현재 표시값 산출
    const clonedOutput: RecordEngineOutput = JSON.parse(JSON.stringify(output));

    this.corrections.forEach((c) => {
      const event = clonedOutput.energyEvents.find((e) => e.id === c.targetEventId);
      if (event && event.fields[c.field as keyof EnergyEventFields]) {
        event.fields[c.field as keyof EnergyEventFields] = { ...c.newField };
      }
    });

    this.currentData = clonedOutput;
    this.notify();
  }

  /**
   * 사용자 정정 입력 (B01-B: 결제금액 65,000원 입력 등)
   */
  public updateEnergyField(
    targetEventId: string,
    field: keyof EnergyEventFields,
    value: number | null,
    editedAt: string = '2026-11-15T08:50:00+09:00'
  ): UserCorrection | null {
    const targetEvent = this.currentData.energyEvents.find((e) => e.id === targetEventId);
    if (!targetEvent || !targetEvent.fields[field]) return null;

    const previousField: ValueField<any> = {
      ...targetEvent.fields[field],
    };

    const newField: ValueField<any> = {
      value,
      provenance: 'USER_INPUT',
    };

    const correction: UserCorrection = {
      correctionId: `correction-${this.correctionCounter++}`,
      targetEventId,
      field,
      previousField,
      newField,
      editedAt,
    };

    // 정정 이력 추가 및 현재 표시 필드 갱신
    this.corrections.push(correction);
    targetEvent.fields[field] = newField;

    // B01-A 명세 준수: 금액만 입력했다고 recordStatus를 자동으로 USER_CONFIRMED로 바꾸지 않음.
    this.notify();
    return correction;
  }

  /**
   * 데모 리셋 (DoD: 초기 상태 복구)
   */
  public reset(initialData: RecordEngineOutput): void {
    this.currentData = JSON.parse(JSON.stringify(initialData));
    this.corrections = [];
    this.correctionCounter = 1;
    this.notify();
  }

  public getData(): RecordEngineOutput {
    return this.currentData;
  }

  public getCorrections(): UserCorrection[] {
    return [...this.corrections];
  }

  public getEnergyEvent(id: string): EnergyEvent | undefined {
    return this.currentData.energyEvents.find((e) => e.id === id);
  }
}
