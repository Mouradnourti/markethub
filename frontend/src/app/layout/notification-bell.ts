import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { AppNotification, NotificationType } from '../core/models/api.models';
import { NotificationService } from '../core/notifications/notification.service';

const ICONS: Record<NotificationType, string> = {
  ORDER_UPDATE: 'local_shipping',
  NEW_ORDER: 'shopping_cart_checkout',
  BACK_IN_STOCK: 'inventory_2',
  PRICE_DROP: 'trending_down',
  REVIEW_REPLY: 'forum',
  CART_REMINDER: 'shopping_bag',
};

/** Bell with unread badge; new notifications also pop up as a toast with a "View" action. */
@Component({
  selector: 'app-notification-bell',
  imports: [MatMenuModule, MatIconModule, MatBadgeModule, DatePipe, TranslatePipe],
  template: `
    <button type="button" class="bell" [matMenuTriggerFor]="menu" (menuOpened)="opened()"
            [attr.aria-label]="'Notifications' | t" [attr.data-unread]="service.unread()">
      <mat-icon [matBadge]="service.unread() || null" matBadgeSize="small" matBadgeColor="warn" aria-hidden="false">
        notifications
      </mat-icon>
      @if (service.connected()) { <span class="live" [attr.title]="'Live updates on' | t"></span> }
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="bell-menu">
      <div class="head" (click)="$event.stopPropagation()">
        <strong>{{ 'Notifications' | t }}</strong>
        @if (service.unread()) {
          <button type="button" class="link" (click)="service.markAllRead()">{{ 'Mark all as read' | t }}</button>
        }
      </div>
      @for (n of service.items(); track n.id) {
        <button mat-menu-item class="item" [class.unread]="!n.read" (click)="open(n)">
          <span class="icon"><mat-icon>{{ icon(n) }}</mat-icon></span>
          <span class="text">
            <span class="title">{{ n.title }}</span>
            @if (n.body) { <span class="body">{{ n.body }}</span> }
            <span class="when">{{ n.createdAt | date: 'd MMM, HH:mm' }}</span>
          </span>
        </button>
      } @empty {
        <div class="empty">
          <mat-icon>notifications_off</mat-icon>
          <span>{{ 'You are all caught up.' | t }}</span>
        </div>
      }
    </mat-menu>
  `,
  styles: `
    .bell { position: relative; display: grid; place-items: center; width: 42px; height: 42px; border: 0; border-radius: 50%;
      background: transparent; color: var(--mh-text); cursor: pointer; }
    .bell:hover { background: var(--mh-surface-2); }
    .live { position: absolute; bottom: 8px; inset-inline-end: 8px; width: 7px; height: 7px; border-radius: 50%; background: #22c55e;
      box-shadow: 0 0 0 2px var(--mh-surface); }
    .head { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 10px 16px 8px; }
    .link { border: 0; background: none; color: var(--mh-accent); font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
    .item { height: auto !important; min-height: 64px; padding-block: 10px !important; white-space: normal; line-height: 1.35; }
    .item.unread { background: var(--mh-accent-soft); }
    .icon { display: inline-grid; place-items: center; flex-shrink: 0; width: 36px; height: 36px; margin-inline-end: 12px;
      border-radius: 10px; background: var(--mh-surface-2); vertical-align: top; }
    .icon mat-icon { margin: 0 !important; color: var(--mh-text) !important; }
    .text { display: inline-flex; flex-direction: column; max-width: 270px; vertical-align: top; }
    .title { font-size: 14px; font-weight: 600; }
    .body { color: var(--mh-text-muted); font-size: 13px; }
    .when { margin-top: 2px; color: var(--mh-text-muted); font-size: 11px; }
    .empty { display: flex; flex-direction: column; gap: 6px; align-items: center; padding: 22px 40px; color: var(--mh-text-muted); font-size: 14px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBell {
  protected readonly service = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  constructor() {
    const snackBar = inject(MatSnackBar);
    this.service.incoming.pipe(takeUntilDestroyed()).subscribe((n) => {
      const ref = snackBar.open(n.title, n.link ? this.i18n.t('View') : this.i18n.t('OK'), {
        duration: 6000,
        horizontalPosition: this.i18n.rtl ? 'left' : 'right',
        verticalPosition: 'top',
        panelClass: 'snack-live',
      });
      ref.onAction().subscribe(() => this.open(n));
    });
  }

  protected icon(n: AppNotification): string {
    return ICONS[n.type] ?? 'notifications';
  }

  protected opened(): void {
    this.service.load().subscribe({ error: () => undefined });
  }

  protected open(n: AppNotification): void {
    this.service.markRead(n);
    if (n.link) {
      void this.router.navigateByUrl(n.link);
    }
  }
}
