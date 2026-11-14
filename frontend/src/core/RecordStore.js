/**
 * RecordStore (ES Module)
 * F03 핵심 저장소: 엔진 산출물 관리, 사용자 정정(UserCorrection) 기록 및 원본 불변성 보장
 */
export class RecordStore {
  constructor(initialData) {
    this.currentData = JSON.parse(JSON.stringify(initialData));
    this.corrections = [];
    this.listeners = [];
    this.correctionCounter = 1;
  }

  subscribe(listener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  notify() {
    this.listeners.forEach((l) => l(this));
  }

  updateEngineOutput(output) {
    const clonedOutput = JSON.parse(JSON.stringify(output));

    this.corrections.forEach((c) => {
      const event = clonedOutput.energyEvents.find((e) => e.id === c.targetEventId);
      if (event && event.fields[c.field]) {
        event.fields[c.field] = { ...c.newField };
      }
    });

    this.currentData = clonedOutput;
    this.notify();
  }

  updateEnergyField(
    targetEventId,
    field,
    value,
    editedAt = '2026-11-15T08:50:00+09:00'
  ) {
    const targetEvent = this.currentData.energyEvents.find((e) => e.id === targetEventId);
    if (!targetEvent || !Object.hasOwn(targetEvent.fields, field)) return null;

    const previousField = {
      ...targetEvent.fields[field],
    };

    const newField = {
      value,
      provenance: 'USER_INPUT',
    };

    const correction = {
      correctionId: `correction-${this.correctionCounter++}`,
      targetEventId,
      field,
      previousField,
      newField,
      editedAt,
    };

    this.corrections.push(correction);
    targetEvent.fields[field] = newField;

    this.notify();
    return correction;
  }

  reset(initialData) {
    this.currentData = JSON.parse(JSON.stringify(initialData));
    this.corrections = [];
    this.correctionCounter = 1;
    this.notify();
  }

  getData() {
    return this.currentData;
  }

  getCorrections() {
    return [...this.corrections];
  }

  getEnergyEvent(id) {
    return this.currentData.energyEvents.find((e) => e.id === id);
  }
}
