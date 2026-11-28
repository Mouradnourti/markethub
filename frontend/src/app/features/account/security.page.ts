import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { toDataURL } from 'qrcode';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Notifier } from '../../core/ui/notifier.service';

/** Two-factor sign-in setup: scan a QR code with an authenticator app, then confirm with a code. */
@Component({
  selector: 'app-security-page',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatIconModule, TranslatePipe],
  template: `
    <div class="container page narrow">
      <h1>{{ 'Sign-in & security' | t }}</h1>
      <p class="muted">{{ 'Protect your account with a second step at sign-in.' | t }}</p>

      <section class="panel card">
        <div class="head">
          <span class="badge" [class.on]="enabled()"><mat-icon>{{ enabled() ? 'verified_user' : 'shield' }}</mat-icon></span>
          <div>
            <h2>{{ 'Two-step verification' | t }}</h2>
            <p class="muted">
              {{ (enabled() ? 'On. Each sign-in asks for a code from your authenticator app.'
                            : 'Off. Recommended for sellers and administrators.') | t }}
            </p>
          </div>
        </div>

        @if (!enabled() && setup(); as s) {
          <ol class="steps">
            <li>{{ 'Open an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password…).' | t }}</li>
            <li>{{ 'Scan this QR code, or type the key by hand.' | t }}</li>
          </ol>
          <div class="qr">
            @if (qr()) { <img [src]="qr()" [alt]="'QR code for the authenticator app' | t" width="200" height="200" /> }
            <code dir="ltr">{{ s.secret }}</code>
          </div>
        }

        @if (setup() || enabled()) {
          <form class="confirm" (ngSubmit)="confirm()">
            <mat-form-field>
              <mat-label>{{ '6-digit code' | t }}</mat-label>
              <input matInput name="code" [(ngModel)]="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" />
            </mat-form-field>
            <button mat-flat-button [class]="enabled() ? 'btn-light' : 'btn-ink'" [disabled]="busy() || code.trim().length !== 6">
              {{ (enabled() ? 'Turn off' : 'Turn on') | t }}
            </button>
          </form>
        } @else {
          <button mat-flat-button class="btn-ink" (click)="start()" [disabled]="busy()">
            <mat-icon>qr_code_2</mat-icon> {{ 'Set up two-step verification' | t }}
          </button>
        }
      </section>
    </div>
  `,
  styles: `
    .narrow { max-width: 720px; }
    h1 { margin: 0 0 6px; font-size: 32px; }
    .card { display: flex; flex-direction: column; gap: 18px; margin-top: 24px; padding: 28px; }
    .head { display: flex; gap: 16px; align-items: flex-start; }
    .head h2 { margin: 0 0 4px; font-size: 20px; }
    .head p { margin: 0; }
    .badge { display: grid; flex-shrink: 0; place-items: center; width: 48px; height: 48px; border-radius: 14px;
      background: var(--mh-surface-2); color: var(--mh-text-muted); }
    .badge.on { background: var(--mh-success-bg); color: var(--mh-success); }
    .steps { margin: 0; padding-inline-start: 20px; line-height: 1.7; }
    .qr { display: flex; flex-wrap: wrap; gap: 20px; align-items: center; }
    .qr img { border: 1px solid var(--mh-border); border-radius: 14px; }
    code { padding: 10px 14px; border-radius: 10px; background: var(--mh-surface-2); font-size: 15px; letter-spacing: .08em; word-break: break-all; }
    .confirm { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
    .confirm mat-form-field { width: 200px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityPage {
  private readonly auth = inject(AuthService);
  private readonly notifier = inject(Notifier);
  private readonly i18n = inject(I18nService);
  protected readonly enabled = signal(this.auth.user()?.twoFactorEnabled ?? false);
  protected readonly setup = signal<{ secret: string; otpauthUri: string } | null>(null);
  protected readonly qr = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected code = '';

  protected start(): void {
    this.busy.set(true);
    this.auth.twoFactorSetup().subscribe({
      next: (setup) => {
        this.setup.set(setup);
        this.busy.set(false);
        void toDataURL(setup.otpauthUri, { width: 200, margin: 1 }).then((url) => this.qr.set(url));
      },
      error: () => this.busy.set(false),
    });
  }

  protected confirm(): void {
    const turnOn = !this.enabled();
    this.busy.set(true);
    this.auth.setTwoFactor(turnOn, this.code.trim()).subscribe({
      next: (result) => {
        this.enabled.set(result.enabled);
        this.setup.set(null);
        this.qr.set(null);
        this.code = '';
        this.busy.set(false);
        this.notifier.success(this.i18n.t(result.enabled ? 'Two-step verification is on.' : 'Two-step verification is off.'));
      },
      error: () => this.busy.set(false),
    });
  }
}
