import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { AdminApi } from '../../core/api/admin.api';
import { AuthService } from '../../core/auth/auth.service';
import { Page, Role, User } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { StatusChip } from '../../shared/ui/status-chip';

@Component({
  selector: 'app-admin-users-page',
  imports: [
    DatePipe,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatSlideToggleModule,
    StatusChip,
  ],
  template: `
    <div class="page-title"><div><h1>Users</h1><p>{{ page()?.totalElements ?? 0 }} accounts</p></div></div>
    <div class="toolbar">
      <mat-button-toggle-group [value]="role()" (change)="role.set($event.value); load(0)" aria-label="Role filter">
        <mat-button-toggle [value]="null">All</mat-button-toggle>
        <mat-button-toggle value="CUSTOMER">Customers</mat-button-toggle>
        <mat-button-toggle value="SELLER">Sellers</mat-button-toggle>
        <mat-button-toggle value="ADMIN">Admins</mat-button-toggle>
      </mat-button-toggle-group>
      <mat-form-field class="search">
        <mat-label>Search by name or email</mat-label>
        <input matInput [(ngModel)]="query" (keyup.enter)="load(0)" />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
    </div>
    <section class="panel table-wrap">
      @if (page(); as p) {
        <table mat-table [dataSource]="p.content">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let u"><strong>{{ u.firstName }} {{ u.lastName }}</strong><div class="muted small">{{ u.email }}</div></td>
          </ng-container>
          <ng-container matColumnDef="role">
            <th mat-header-cell *matHeaderCellDef>Role</th>
            <td mat-cell *matCellDef="let u"><app-status-chip status="NEUTRAL" [text]="u.role" /></td>
          </ng-container>
          <ng-container matColumnDef="joined">
            <th mat-header-cell *matHeaderCellDef>Joined</th>
            <td mat-cell *matCellDef="let u">{{ u.createdAt | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container matColumnDef="enabled">
            <th mat-header-cell *matHeaderCellDef>Active</th>
            <td mat-cell *matCellDef="let u">
              <mat-slide-toggle [checked]="u.enabled" [disabled]="u.id === currentUserId" (change)="toggle(u, $event.checked)"
                                [attr.aria-label]="'Enable ' + u.email" />
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true" (page)="load($event.pageIndex)" />
      }
    </section>
  `,
  styles: `
    .toolbar { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; margin-bottom: 16px; }
    .search { width: 320px; }
    .small { font-size: 12px; }
    mat-paginator { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUsersPage {
  private readonly api = inject(AdminApi);
  private readonly notifier = inject(Notifier);
  protected readonly currentUserId = inject(AuthService).user()?.id;
  protected readonly role = signal<Role | null>(null);
  protected readonly page = signal<Page<User> | null>(null);
  protected query = '';
  protected readonly columns = ['name', 'role', 'joined', 'enabled'];

  constructor() {
    this.load(0);
  }

  protected load(pageIndex: number): void {
    this.api.users(this.role(), this.query, pageIndex, 15).subscribe((page) => this.page.set(page));
  }

  protected toggle(user: User, enabled: boolean): void {
    this.api.setUserEnabled(user.id, enabled).subscribe({
      next: () => this.notifier.success(`${user.email} ${enabled ? 'enabled' : 'disabled'}`),
      error: () => this.load(this.page()?.page ?? 0),
    });
  }
}
