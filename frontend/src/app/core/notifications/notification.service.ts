import { HttpClient, HttpContext } from '@angular/common/http';
import { DestroyRef, Injectable, effect, inject, signal } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { SILENT_ERRORS } from '../http/error.interceptor';
import { AppNotification, Page } from '../models/api.models';

interface LiveEvent {
  notification: AppNotification;
  unread: number;
}

const MIN_RETRY_MS = 3_000;
const MAX_RETRY_MS = 60_000;

/**
 * The bell menu: notifications pushed live by the backend over server-sent events. The stream is opened with a
 * one-time ticket (EventSource cannot send the JWT header) and reopened with a new ticket after a drop.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly silent = new HttpContext().set(SILENT_ERRORS, true);
  private source: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelay = MIN_RETRY_MS;

  readonly items = signal<AppNotification[]>([]);
  readonly unread = signal(0);
  readonly connected = signal(false);
  /** Every notification received live, for toasts and pages that refresh themselves. */
  readonly incoming = new Subject<AppNotification>();

  constructor() {
    effect(() => {
      if (this.auth.user()) {
        this.refreshUnread();
        this.connect();
      } else {
        this.disconnect();
        this.items.set([]);
        this.unread.set(0);
      }
    });
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  load(): Observable<Page<AppNotification>> {
    return this.http
      .get<Page<AppNotification>>('/api/notifications', { params: { size: 15 }, context: this.silent })
      .pipe(tap((page) => this.items.set(page.content)));
  }

  markRead(notification: AppNotification): void {
    if (notification.read) {
      return;
    }
    this.items.update((list) => list.map((n) => (n.id === notification.id ? { ...n, read: true } : n)));
    this.unread.update((count) => Math.max(0, count - 1));
    this.http.post(`/api/notifications/${notification.id}/read`, null, { context: this.silent }).subscribe({
      error: () => this.refreshUnread(),
    });
  }

  markAllRead(): void {
    this.items.update((list) => list.map((n) => ({ ...n, read: true })));
    this.unread.set(0);
    this.http.post('/api/notifications/read-all', null, { context: this.silent }).subscribe({ error: () => undefined });
  }

  private refreshUnread(): void {
    this.http
      .get<{ unread: number }>('/api/notifications/unread-count', { context: this.silent })
      .subscribe({ next: (r) => this.unread.set(r.unread), error: () => undefined });
  }

  private connect(): void {
    if (this.source || typeof EventSource === 'undefined') {
      return;
    }
    this.http.post<{ ticket: string }>('/api/notifications/stream-ticket', null, { context: this.silent }).subscribe({
      next: ({ ticket }) => this.open(ticket),
      error: () => this.scheduleRetry(),
    });
  }

  private open(ticket: string): void {
    if (!this.auth.user()) {
      return;
    }
    const source = new EventSource(`/api/notifications/stream?ticket=${encodeURIComponent(ticket)}`);
    this.source = source;
    source.addEventListener('ready', () => {
      this.connected.set(true);
      this.retryDelay = MIN_RETRY_MS;
    });
    source.addEventListener('notification', (event) => {
      const data = JSON.parse((event as MessageEvent<string>).data) as LiveEvent;
      this.unread.set(data.unread);
      this.items.update((list) => [data.notification, ...list.filter((n) => n.id !== data.notification.id)].slice(0, 30));
      this.incoming.next(data.notification);
    });
    // Tickets are single-use, so let the browser not retry by itself: close and come back with a new ticket.
    source.onerror = () => {
      source.close();
      this.source = null;
      this.connected.set(false);
      this.scheduleRetry();
    };
  }

  private scheduleRetry(): void {
    if (this.retryTimer || !this.auth.user()) {
      return;
    }
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.connect();
    }, this.retryDelay);
    this.retryDelay = Math.min(this.retryDelay * 2, MAX_RETRY_MS);
  }

  private disconnect(): void {
    this.source?.close();
    this.source = null;
    this.connected.set(false);
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}
