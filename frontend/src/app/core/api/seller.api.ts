import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SILENT_ERRORS } from '../http/error.interceptor';
import {
  CopyDraft,
  CopyDraftRequest,
  OrderStatus,
  Page,
  Product,
  ProductRequest,
  ProductSummary,
  SalesForecast,
  SellerOrder,
  SellerStats,
  Shop,
} from '../models/api.models';
import { toParams } from './catalog.api';

@Injectable({ providedIn: 'root' })
export class SellerApi {
  private readonly http = inject(HttpClient);

  shop(): Observable<Shop> {
    return this.http.get<Shop>('/api/seller/shop');
  }

  updateShop(body: { name: string; description?: string; logoUrl?: string }): Observable<Shop> {
    return this.http.put<Shop>('/api/seller/shop', body);
  }

  stats(): Observable<SellerStats> {
    return this.http.get<SellerStats>('/api/seller/stats');
  }

  forecast(days = 30): Observable<SalesForecast> {
    return this.http.get<SalesForecast>('/api/seller/forecast', {
      params: { days },
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  products(page = 0, size = 20): Observable<Page<ProductSummary>> {
    return this.http.get<Page<ProductSummary>>('/api/seller/products', { params: { page, size } });
  }

  product(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/seller/products/${id}`);
  }

  describe(body: CopyDraftRequest): Observable<CopyDraft> {
    return this.http.post<CopyDraft>('/api/seller/products/describe', body);
  }

  createProduct(body: ProductRequest): Observable<Product> {
    return this.http.post<Product>('/api/seller/products', body);
  }

  updateProduct(id: number, body: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`/api/seller/products/${id}`, body);
  }

  updateStock(id: number, stock: number): Observable<Product> {
    return this.http.patch<Product>(`/api/seller/products/${id}/stock`, { stock });
  }

  uploadImage(id: number, file: File): Observable<Product> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Product>(`/api/seller/products/${id}/images`, form);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`/api/seller/products/${id}`);
  }

  orders(status: OrderStatus | null, page = 0, size = 20): Observable<Page<SellerOrder>> {
    return this.http.get<Page<SellerOrder>>('/api/seller/orders', { params: toParams({ status, page, size }) });
  }

  updateOrderStatus(id: number, status: OrderStatus, trackingNumber?: string, note?: string): Observable<SellerOrder> {
    return this.http.patch<SellerOrder>(`/api/seller/orders/${id}/status`, { status, trackingNumber, note });
  }
}
