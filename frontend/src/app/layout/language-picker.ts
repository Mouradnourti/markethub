import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { I18nService, LANGUAGES } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/** Language switcher (English, Français, العربية); Arabic switches the whole interface to right-to-left. */
@Component({
  selector: 'app-language-picker',
  imports: [MatMenuModule, MatIconModule, TranslatePipe],
  template: `
    <button type="button" class="picker" [class.light]="light()" [matMenuTriggerFor]="menu"
            [attr.aria-label]="'Language' | t">
      <mat-icon>language</mat-icon>
      <span>{{ i18n.lang().toUpperCase() }}</span>
    </button>
    <mat-menu #menu="matMenu" xPosition="before">
      @for (language of languages; track language.code) {
        <button mat-menu-item (click)="i18n.use(language.code)" [attr.lang]="language.code"
                [attr.aria-current]="language.code === i18n.lang()">
          <span class="option">{{ language.label }}</span>
          @if (language.code === i18n.lang()) {
            <mat-icon class="check">check</mat-icon>
          }
        </button>
      }
    </mat-menu>
  `,
  styles: `
    .picker { display: inline-flex; gap: 4px; align-items: center; height: 36px; padding: 0 10px; border: 1px solid var(--mh-border);
      border-radius: 999px; background: var(--mh-surface); color: var(--mh-text); font: inherit; font-size: 13px; font-weight: 600;
      cursor: pointer; }
    .picker:hover { border-color: var(--mh-text-muted); }
    .picker.light { border-color: rgb(255 255 255 / 0.25); background: transparent; color: #fff; }
    .picker mat-icon { width: 18px; height: 18px; font-size: 18px; }
    .check { margin-inline-start: auto; color: var(--mh-accent); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguagePicker {
  readonly light = input(false);
  protected readonly i18n = inject(I18nService);
  protected readonly languages = LANGUAGES;
}
