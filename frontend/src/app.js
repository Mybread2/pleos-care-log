import { DemoSession } from './ui/DemoSession.js';
import { icon, routeMap } from './ui/visuals.js';

const $ = (id) => document.getElementById(id);
const number = (value) => value.toLocaleString('ko-KR');
const time = (value) => value.slice(11, 16);
const fieldValue = (field) => field.value === null ? '미확인' : `${number(field.value)}원`;
const provenance = (field) => `<span class="provenance ${field.provenance === 'USER_INPUT' ? 'user' : ''}">${field.provenance}${field.value === null ? ' · null' : ''}</span>`;

class AutoLogApp {
  constructor() {
    this.view = 'welcome';
    this.replaceDraft = true;
    this.session = new DemoSession((state) => this.render(state));
    $('welcome-map').innerHTML = routeMap({ preview: true });
    this.updatePresets();
    this.bindEvents();
    this.render(this.session.snapshot());
    this.renderIcons();
  }

  renderIcons() {
    document.querySelectorAll('[data-icon]').forEach((element) => {
      if (!element.firstElementChild) element.innerHTML = icon(element.dataset.icon);
    });
  }

  bindEvents() {
    document.addEventListener('click', (event) => {
      const button = event.target.closest('button');
      if (!button || button.disabled || button.closest('fieldset:disabled')) return;
      if (button.dataset.view) this.switchView(button.dataset.view);
      if (button.dataset.action) this.handleAction(button.dataset.action);
      if (button.dataset.amount) this.setDraft(button.dataset.amount);
      if (button.dataset.key) this.handleKey(button.dataset.key);
    });
    document.querySelector('.brand').addEventListener('click', (event) => {
      event.preventDefault();
      this.switchView('welcome');
    });
    $('correction-form').addEventListener('submit', (event) => {
      event.preventDefault();
      const result = this.session.correctAmount($('input-amount-krw').value.replaceAll(',', ''));
      $('correction-message').textContent = result.message;
      $('correction-message').className = `form-message ${result.ok ? 'success' : 'error'}`;
      if (result.ok) { this.replaceDraft = true; this.showToast(result.message); }
    });
    $('input-amount-krw').addEventListener('focus', () => { $('input-amount-krw').select(); });
    $('input-amount-krw').addEventListener('input', () => {
      const input = $('input-amount-krw');
      if (/^[\d,]*$/.test(input.value)) {
        const digits = input.value.replaceAll(',', '');
        input.value = digits ? number(Number(digits)) : '';
      }
      this.replaceDraft = false;
      this.updatePresets();
      $('correction-message').textContent = '확인 후 정정 적용을 눌러 주세요.';
      $('correction-message').className = 'form-message';
    });
  }

  switchView(view) {
    if (!['welcome', 'journey', 'refuel', 'correction'].includes(view)) return;
    this.view = view;
    document.querySelectorAll('.screen').forEach((screen) => {
      screen.hidden = screen.id !== view;
      screen.classList.toggle('active', screen.id === view);
    });
    document.querySelectorAll('.rail-button').forEach((button) => {
      const active = button.dataset.view === view;
      button.classList.toggle('active', active);
      if (active) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');
    });
    $('main-content').scrollTop = 0;
    $('main-content').focus({ preventScroll: true });
  }

  handleAction(action) {
    if (action === 'start' || action === 'all') {
      this.session.runAll();
      this.switchView('journey');
    } else if (action === 'next') {
      this.session.next();
      if (this.view === 'welcome') this.switchView('journey');
    } else if (action === 'reset') {
      this.session.reset();
      this.setDraft('65000');
      $('correction-message').textContent = '금액 선택 후 한 번만 적용하면 끝.';
      $('correction-message').className = 'form-message';
      clearTimeout(this.toastTimer);
      $('toast').hidden = true;
      this.switchView('welcome');
    }
  }

  setDraft(value) {
    if (!this.session.snapshot().inputAllowed && value !== '65000') return;
    $('input-amount-krw').value = value ? number(Number(value)) : '';
    this.replaceDraft = true;
    this.updatePresets();
    $('correction-message').textContent = '확인 후 정정 적용을 눌러 주세요.';
    $('correction-message').className = 'form-message';
  }

  updatePresets() {
    const value = $('input-amount-krw').value.replaceAll(',', '');
    document.querySelectorAll('[data-amount]').forEach((button) => {
      const selected = button.dataset.amount === value;
      button.classList.toggle('selected', selected);
      button.setAttribute('aria-pressed', String(selected));
    });
  }

  handleKey(key) {
    if (!this.session.snapshot().inputAllowed) return;
    let digits = $('input-amount-krw').value.replaceAll(',', '');
    if (!/^\d*$/.test(digits)) digits = '';
    if (key === 'clear') digits = '';
    else if (key === 'backspace') digits = digits.slice(0, -1);
    else digits = (this.replaceDraft ? key : digits + key).slice(0, 7);
    this.setDraft(digits);
    this.replaceDraft = false;
  }

  showToast(message) {
    clearTimeout(this.toastTimer);
    $('toast').textContent = `✓ ${message}`;
    $('toast').hidden = false;
    this.toastTimer = setTimeout(() => { $('toast').hidden = true; }, 3500);
  }

  render(state) {
    this.renderStatus(state);
    this.renderJourney(state);
    this.renderRefuel(state);
    this.renderCorrection(state);
    this.renderIcons();
  }

  renderStatus(state) {
    const { step, total, latestEvent, ignition } = state;
    $('vehicle-time').textContent = latestEvent ? time(latestEvent.at) : '08:00';
    $('vehicle-state').innerHTML = `<i></i>${ignition === 'ON' ? '시동 ON · 입력 잠금' : ignition === 'OFF' ? '시동 OFF · 입력 가능' : '차량 신호 대기'}`;
    $('vehicle-state').classList.toggle('locked', ignition === 'ON');
    $('step-counter').textContent = `Step ${step} / ${total}`;
    $('last-signal-ticker').textContent = latestEvent ? `[${latestEvent.eventId}] ${latestEvent.signal} = ${latestEvent.value}` : '시연을 시작해 주세요';
    $('event-progress').innerHTML = Array.from({ length: total }, (_, index) => `<i class="${index < step ? 'done' : ''}"></i>`).join('');
    $('event-progress').setAttribute('aria-valuenow', String(step));
    $('btn-next').disabled = step === total;
    $('btn-run-all').disabled = step === total;
    $('refuel-dot').hidden = !state.data.energyEvents.length || state.corrections.length > 0;
  }

  renderJourney(state) {
    const { data, step, total, ignition, events } = state;
    const journey = data.journeys[0];
    $('total-distance').textContent = journey ? journey.distanceKm.toFixed(1) : '—';
    $('total-duration').textContent = journey ? String(Math.round(journey.activeDurationSec / 60)) : '—';
    $('journey-status').innerHTML = `<span class="live-dot"></span>${step === total ? '오늘의 여정이 기록되었어요' : step ? '차량 신호를 기록하고 있어요' : '모의 주행을 기다리고 있어요'}`;
    $('leg-count').textContent = `${data.legs.length}개 구간`;
    $('journey-basis').textContent = journey ? journey.groupingBasis : '기록 대기';
    $('journey-map').innerHTML = routeMap({ completed: data.legs.length, started: step > 0 });
    const stop = events.find((event) => event.signal === 'IGNITION' && event.value === 'OFF');
    const restart = stop && events.find((event) => event.signal === 'IGNITION' && event.value === 'ON' && event.at > stop.at);
    const pauseMinutes = restart ? Math.round((Date.parse(restart.at) - Date.parse(stop.at)) / 60000) : null;
    $('journey-metric-note').textContent = pauseMinutes !== null ? `정차 ${pauseMinutes}분 제외` : '완료된 구간 기준';
    let timeline = '';
    data.legs.forEach((leg, index) => {
      timeline += `<article class="leg"><div class="leg-marker">${icon(index === 0 ? 'home' : 'fuel')}</div><div class="leg-info"><span class="leg-caption">구간 ${index + 1}</span><div class="leg-title"><h3>${index === 0 ? '집 <span>→</span> 주유소' : '주유소 <span>→</span> 회사'}</h3><strong>${leg.distanceKm}<small> km</small></strong></div><p>${time(leg.startedAt)} — ${time(leg.endedAt)} <b>· ${Math.round(leg.activeDurationSec / 60)}분 운행</b></p><p class="odometer">${number(leg.startOdometerKm)} → ${number(leg.endOdometerKm)} km</p></div></article>`;
      if (index === 0 && stop) {
        timeline += `<div class="pause-row">${icon('pause')}<div><strong>${pauseMinutes !== null ? `정차 ${pauseMinutes}분` : '정차 중'}</strong><span>${time(stop.at)}${restart ? ` — ${time(restart.at)}` : ' 이후'} · 시동 OFF · 운행시간 제외</span></div></div>`;
      }
    });
    if (!data.legs.length) timeline = `<div class="timeline-empty">${icon('route')}<strong>${ignition === 'ON' ? '첫 구간을 기록하고 있어요' : '아직 완료된 구간이 없어요'}</strong><p>시동 OFF 신호가 도착하면<br>주행거리와 운행시간이 집계됩니다.</p></div>`;
    else if (ignition === 'ON') timeline += '<p class="recording-line"><span class="live-dot"></span>다음 주행 구간 기록 중</p>';
    $('legs-timeline').innerHTML = timeline;
    $('journey-refuel').innerHTML = data.energyEvents.length ? `${icon('fuel')}<div><strong>주유가 감지되었어요</strong><span>${data.energyEvents[0].fields.amountKrw.value === null ? '결제금액을 확인해 주세요' : `${fieldValue(data.energyEvents[0].fields.amountKrw)} · 사용자 입력`}</span></div><span class="count-pill">1</span>` : `${icon('shield')}<div><strong>별도의 입력 없이</strong><span>차량 신호에서 자동으로 기록합니다</span></div>`;
  }

  renderRefuel(state) {
    const refuel = state.data.energyEvents[0];
    $('refuel-status').innerHTML = refuel ? '<span class="amber-dot"></span>감지됨 · 확인 대기' : '감지된 주유 없음';
    if (!refuel) {
      $('refuel-content').innerHTML = `<div class="empty-state">${icon('fuel')}<h2>아직 감지된 주유가 없어요</h2><p>연료가 증가한 뒤 시동이 켜지면 주유 기록이 생성됩니다.<br>전체 시연으로 연료 30% → 75% 변화를 확인해 보세요.</p><button class="button primary" data-action="all">전체 시연 실행 ${icon('forward')}</button></div>`;
      return;
    }
    const fields = refuel.fields;
    const before = fields.levelBeforePercent.value;
    const after = fields.levelAfterPercent.value;
    $('refuel-content').innerHTML = `<div class="refuel-layout"><div class="fuel-panel"><div class="fuel-heading"><span class="fuel-icon">${icon('fuel')}</span><div><h2>연료의 변화를 기록했어요</h2><p>${time(refuel.detectedAt)} 감지 · 누적 ${number(refuel.odometerKm)} km</p></div><span class="subtle-tag">OBSERVED</span></div><div class="fuel-comparison"><div><span>주유 전</span><p>${before}<small>%</small></p></div>${icon('arrow')}<div><span>주유 후</span><p>${after}<small>%</small></p></div></div><div class="fuel-gauge"><div style="width:${before}%"></div><div style="width:${after - before}%"></div></div><div class="gauge-scale"><span>0</span><span>연료 잔량</span><span>100%</span></div><div class="fuel-increase"><span>관측된 연료 증가</span><strong>+${after - before}<small> %p</small></strong></div><p class="sensor-note">${icon('shield')}차량 센서가 관측한 연료 잔량을 그대로 보존합니다.</p></div><div class="fuel-record-panel"><div class="section-heading"><h2>확인이 필요한 정보</h2><span class="subtle-tag amber">확인 대기</span></div><div class="unknown-field"><div><span>주유량</span>${provenance(fields.quantityLiters)}</div><strong>${fields.quantityLiters.value === null ? '미확인' : `${number(fields.quantityLiters.value)} L`}</strong></div><div class="unknown-field"><div><span>결제금액</span>${provenance(fields.amountKrw)}</div><strong class="${fields.amountKrw.value !== null ? 'blue' : ''}">${fieldValue(fields.amountKrw)}</strong></div><p class="unknown-explanation">차량 센서로 알 수 없는 주유량과 결제금액은<br><strong>미확인 상태로 보존</strong>합니다.</p><div class="record-status"><span>기록 상태</span><code>${refuel.recordStatus}</code></div><button class="button primary full" data-view="correction" ${!state.inputAllowed ? 'disabled' : ''}>${state.inputAllowed ? '금액 정정하러 가기' : '시동 OFF 후 정정 가능'} ${icon(state.inputAllowed ? 'arrow' : 'lock')}</button><p class="safety-note">${icon('lock')}금액 입력은 시동 OFF 상태에서만 가능합니다.</p></div></div>`;
  }

  renderCorrection(state) {
    const { data, corrections, inputAllowed, ignition } = state;
    const refuel = data.energyEvents[0];
    const fields = refuel?.fields;
    $('amount-controls').disabled = !inputAllowed || !refuel;
    $('input-lock').hidden = !!(inputAllowed && refuel);
    $('input-lock').querySelector('strong').textContent = ignition === 'ON' ? '시동 OFF 후 입력할 수 있어요' : '주유 기록을 먼저 감지해 주세요';
    $('input-lock').querySelector('p').innerHTML = ignition === 'ON' ? '안전을 위해 시동 ON 동안<br>금액 입력을 잠급니다.' : '하단의 전체 실행으로<br>모의 주행을 완료해 주세요.';
    $('current-record').innerHTML = `<div class="section-heading"><h2>현재 저장된 기록</h2><span class="subtle-tag">${refuel ? 'REFUEL · fuel-1' : '기록 대기'}</span></div><div class="saved-amount"><span>결제금액</span><div><strong class="${fields?.amountKrw.value != null ? 'blue' : ''}">${fields ? fieldValue(fields.amountKrw) : '—'}</strong>${fields ? provenance(fields.amountKrw) : ''}</div></div><div class="saved-details"><span>주유량 <b>${fields ? (fields.quantityLiters.value === null ? '미확인 · UNKNOWN' : `${number(fields.quantityLiters.value)} L`) : '—'}</b></span><span>감지 시각 <b>${refuel ? `${time(refuel.detectedAt)} · 모의 시각` : '—'}</b></span></div>`;
    $('correction-count').textContent = `${corrections.length}건`;
    $('corrections-table-body').innerHTML = corrections.length ? [...corrections].reverse().map((correction) => {
      const editedTime = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(correction.editedAt));
      return `<tr><td>${editedTime}<small>${correction.correctionId}</small></td><td>결제금액<small>amountKrw</small></td><td>${fieldValue(correction.previousField)}${provenance(correction.previousField)}</td><td class="blue">${fieldValue(correction.newField)}${provenance(correction.newField)}</td></tr>`;
    }).join('') : '<tr><td colspan="4" class="audit-empty">아직 정정 이력이 없습니다.<br><span>금액을 적용하면 변경 전후의 값이 여기에 남습니다.</span></td></tr>';
    const rawFuel = state.events.filter((event) => event.signal === 'FUEL_PERCENT');
    const original = rawFuel.length ? rawFuel.map((event) => `<span>${event.eventId} <b>${event.value}%</b></span>`).join('') : '<span>센서 신호 수집 대기</span>';
    $('raw-preservation').innerHTML = `${icon('shield')}<div><strong>원본 Mock 센서 데이터 · 변경 없음</strong><div class="raw-values">${original}</div></div><span class="subtle-tag">OBSERVED</span>`;
  }
}

new AutoLogApp();
