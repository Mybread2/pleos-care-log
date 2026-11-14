const paths = {
  logo: '<path d="m4 21 8-18 8 18M7 15h10M12 5v4"/>',
  home: '<path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9"/>',
  route: '<circle cx="6" cy="5" r="2"/><circle cx="18" cy="19" r="2"/><path d="M8 5h7a4 4 0 0 1 0 8H9a4 4 0 0 0 0 8h5"/>',
  fuel: '<path d="M4 21V4h10v17M2 21h14M4 10h10M17 5l3 3v9a2 2 0 0 1-4 0v-4h-2M18 6v4h2"/>',
  edit: '<path d="M12 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-7M9 15l1-4L19 2l3 3-9 9-4 1Z"/>',
  shield: '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z"/><path d="m8 12 3 3 5-6"/>',
  arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
  play: '<path d="m8 5 11 7-11 7V5Z"/>',
  forward: '<path d="m3 5 8 7-8 7V5Zm10 0 8 7-8 7V5Z"/>',
  reset: '<path d="M3 10a9 9 0 1 1 2 8M3 4v6h6"/>',
  check: '<path d="m5 12 4 4L20 5"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/>',
  car: '<path d="m4 9 2-5h12l2 5M3 10v9h3v-3h12v3h3v-9H3ZM6 12h2m8 0h2"/>',
  backspace: '<path d="m3 12 6-8h12v16H9l-6-8Zm9-3 6 6m0-6-6 6"/>',
  pause: '<path d="M9 5v14M15 5v14"/>',
  office: '<path d="M5 21V3h14v18M3 21h18M9 7h1m4 0h1M9 11h1m4 0h1M10 21v-5h4v5"/>',
};

export const icon = (name, className = '') => `<svg class="icon ${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] ?? paths.route}</svg>`;

// Schematic only: the fixture has no GPS or real place coordinates.
export function routeMap({ preview = false, completed = 0, started = false } = {}) {
  const first = preview || completed >= 1;
  const second = preview || completed >= 2;
  const pattern = preview ? 'intro-blocks' : 'trip-blocks';
  return `<svg class="route-map" viewBox="0 0 800 410" role="img" aria-label="모의 경로: 집에서 주유소를 거쳐 회사. ${preview ? '서비스 소개용 경로' : `완료된 구간 ${completed}개`}">
    <defs><pattern id="${pattern}" width="118" height="92" patternTransform="rotate(-24)" patternUnits="userSpaceOnUse"><rect x="12" y="10" width="88" height="64" rx="4" fill="#141a22"/><path d="M0 85h118M109 0v92" stroke="#242d38" stroke-width="2"/></pattern></defs>
    <rect width="800" height="410" fill="url(#${pattern})"/>
    <path d="M-30 315C150 220 265 370 440 314S660 140 840 195" fill="none" stroke="#172738" stroke-width="52"/>
    <path d="M-30 315C150 220 265 370 440 314S660 140 840 195" fill="none" stroke="#1b3044" stroke-width="1"/>
    <g fill="#1b2928"><path d="m16 28 127 15-16 87-99-25Z"/><path d="m586 318 90-42 79 55-55 69-111-17Z"/><path d="m383 25 65 5 2 52-64 27-24-37Z"/></g>
    <g fill="none" stroke="#303b48" stroke-width="11" stroke-linejoin="round"><path d="M-20 162 210 162 335 90 820 90"/><path d="m90 440 90-163L340 190l127-22 85-118 90-85"/><path d="m-20 380 238-95 170-65 210 70 202-6"/><path d="m470 440 9-111 110-142L760-30"/></g>
    <g fill="none" stroke="#19212c" stroke-width="7" stroke-linejoin="round"><path d="M-20 162 210 162 335 90 820 90"/><path d="m90 440 90-163L340 190l127-22 85-118 90-85"/><path d="m-20 380 238-95 170-65 210 70 202-6"/><path d="m470 440 9-111 110-142L760-30"/></g>
    <g fill="#647180" font-family="Segoe UI, Malgun Gothic, sans-serif" font-size="12" letter-spacing="2"><text x="46" y="230">주거 지역</text><text x="355" y="365" fill="#607f98">RIVER</text><text x="624" y="150">업무 지구</text></g>
    <path d="M170 274 322 190 465 168 521 90H640" fill="none" stroke="#34414f" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>
    ${first ? '<path d="M170 274 322 190 418 175" fill="none" stroke="#8bb9f2" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>' : ''}
    ${second ? '<path d="m418 175 47-7 56-78h119" fill="none" stroke="#8bb9f2" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>' : ''}
    <circle cx="170" cy="274" r="8" fill="${started || preview ? '#accdf8' : '#697584'}" stroke="#101720" stroke-width="4"/>
    <circle cx="418" cy="175" r="9" fill="${first ? '#accdf8' : '#697584'}" stroke="#101720" stroke-width="4"/>
    <circle cx="640" cy="90" r="10" fill="${second ? '#accdf8' : '#697584'}" stroke="#101720" stroke-width="4"/>
    <g font-family="Segoe UI, Malgun Gothic, sans-serif" text-anchor="middle"><rect x="128" y="294" width="84" height="52" rx="7" fill="#0a0f15" stroke="#2e3947"/><text x="170" y="316" fill="#f1f4f8" font-size="17">집</text><text x="170" y="334" fill="#9aa7b5" font-size="12">${started || preview ? '08:00 출발' : '출발 대기'}</text>
    <rect x="368" y="98" width="100" height="52" rx="7" fill="#0a0f15" stroke="#2e3947"/><text x="418" y="120" fill="#f1f4f8" font-size="17">주유소</text><text x="418" y="138" fill="#9aa7b5" font-size="12">${first ? '08:15 · 경유' : '경유지'}</text>
    <rect x="595" y="112" width="90" height="52" rx="7" fill="#0a0f15" stroke="#2e3947"/><text x="640" y="134" fill="#f1f4f8" font-size="17">회사</text><text x="640" y="152" fill="#9aa7b5" font-size="12">${second ? '08:45 도착' : '도착 대기'}</text></g>
    <g transform="translate(739 337)"><circle r="21" fill="#0d141d" stroke="#394451"/><path d="m0-13 7 17-7-3-7 3Z" fill="#a8b7c8"/><text y="38" text-anchor="middle" fill="#788696" font-family="sans-serif" font-size="11">N</text></g>
  </svg>`;
}
