import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminStats,
  Category,
  Dispute,
  DisputeStatus,
  Page,
  PlatformSettings,
  Role,
  ShopAdmin,
  ShopStatus,
  User,
} from '../models/api.models';
import { toParams } from './catalog.api';

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>('/api/admin/stats');
  }

  users(role: Role | null, q: string, page = 0, size = 20): Observable<Page<User>> {
    return this.http.get<Page<User>>('/api/admin/users', { params: toParams({ role, q, page, size }) });
  }

  setUserEnabled(id: number, enabled: boolean): Observable<User> {
    return this.http.patch<User>(`/api/admin/users/${id}/enabled`, { enabled });
  }

  shops(status: ShopStatus | null, page = 0, size = 20): Observable<Page<ShopAdmin>> {
    return this.http.get<Page<ShopAdmin>>('/api/admin/shops', { params: toParams({ status, page, size }) });
  }

  setShopStatus(id: number, status: ShopStatus): Observable<ShopAdmin> {
    return this.http.patch<ShopAdmin>(`/api/admin/shops/${id}/status`, { status });
  }

  setShopCommission(id: number, commissionRate: number | null): Observable<ShopAdmin> {
    return this.http.patch<ShopAdmin>(`/api/admin/shops/${id}/commission`, { commissionRate });
  }

  createCategory(body: { name: string; description?: string; parentId?: number | null }): Observable<Category> {
    return this.http.post<Category>('/api/admin/categories', body);
  }

  updateCategory(id: number, body: { name: string; description?: string; parentId?: number | null }): Observable<Category> {
    return this.http.put<Category>(`/api/admin/categories/${id}`, body);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/categories/${id}`);
  }

  settings(): Observable<PlatformSettings> {
    return this.http.get<PlatformSettings>('/api/admin/settings');
  }

  updateSettings(body: PlatformSettings): Observable<PlatformSettings> {
    return this.http.put<PlatformSettings>('/api/admin/settings', body);
  }

  disputes(status: DisputeStatus | null, page = 0, size = 20): Observable<Page<Dispute>> {
    return this.http.get<Page<Dispute>>('/api/admin/disputes', { params: toParams({ status, page, size }) });
  }

  resolveDispute(id: number, decision: DisputeStatus, note: string): Observable<Dispute> {
    return this.http.patch<Dispute>(`/api/admin/disputes/${id}`, { decision, note });
  }
}
