import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SILENT_ERRORS } from '../http/error.interceptor';
import {
  CheckoutResponse,
  Dispute,
  DisputeType,
  Order,
  Page,
  PaymentMethod,
  PaymentStatus,
  ProductSummary,
  ShippingAddress,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);

  checkout(paymentMethod: PaymentMethod, shippingAddress: ShippingAddress): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>('/api/orders/checkout', { paymentMethod, shippingAddress });
  }

  confirmPayment(orderId: number, simulateDecline = false): Observable<{ paymentStatus: PaymentStatus }> {
    return this.http.post<{ paymentStatus: PaymentStatus }>(`/api/payments/${orderId}/confirm`, { simulateDecline });
  }

  list(page = 0, size = 10): Observable<Page<Order>> {
    return this.http.get<Page<Order>>('/api/orders', { params: { page, size } });
  }

  get(id: number): Observable<Order> {
    return this.http.get<Order>(`/api/orders/${id}`);
  }

  cancel(id: number, reason?: string): Observable<Order> {
    return this.http.post<Order>(`/api/orders/${id}/cancel`, { reason });
  }

  requestReturn(shopOrderId: number, type: DisputeType, reason: string): Observable<Dispute> {
    return this.http.post<Dispute>(`/api/orders/shop-orders/${shopOrderId}/return-request`, { type, reason });
  }

  invoice(orderId: number): Observable<Blob> {
    return this.http.get(`/api/orders/${orderId}/invoice`, { responseType: 'blob' });
  }

  /** Personalised picks; silently empty when the AI service is unavailable. */
  recommendations(limit = 8): Observable<ProductSummary[]> {
    return this.http.get<ProductSummary[]>('/api/recommendations', {
      params: { limit },
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }
}
