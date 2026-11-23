import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  template: `
    <span class="logo" [class.light]="light()">
      <svg viewBox="0 0 64 64" width="34" height="34" aria-hidden="true">
        <defs>
          <linearGradient id="mh-logo" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#6366f1" /><stop offset="1" stop-color="#7c3aed" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" rx="16" fill="url(#mh-logo)" />
        <path d="M18 24h28l-3 22H21z" fill="none" stroke="#fff" stroke-width="4" stroke-linejoin="round" />
        <path d="M25 24a7 7 0 0 1 14 0" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" />
      </svg>
      <span class="word">Market<b>Hub</b></span>
    </span>
  `,
  styles: `
    .logo { display: inline-flex; align-items: center; gap: 10px; }
    .word { font-size: 21px; font-weight: 600; color: var(--mh-text); letter-spacing: -0.02em; }
    .word b { color: var(--mh-accent); }
    .light .word { color: #fff; }
    .light .word b { color: #a5b4fc; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Logo {
  readonly light = input(false);
}
