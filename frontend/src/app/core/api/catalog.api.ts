import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import {
  Category,
  Page,
  Product,
  ProductQuery,
  ProductSummary,
  Review,
  ReviewEligibility,
  Shop,
} from '../models/api.models';

export function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private categories$?: Observable<Category[]>;

  search(query: ProductQuery): Observable<Page<ProductSummary>> {
    return this.http.get<Page<ProductSummary>>('/api/products', { params: toParams(query) });
  }

  product(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
  }

  similar(id: number, limit = 6): Observable<ProductSummary[]> {
    return this.http.get<ProductSummary[]>(`/api/products/${id}/similar`, { params: { limit } });
  }

  boughtTogether(id: number, limit = 2): Observable<ProductSummary[]> {
    return this.http.get<ProductSummary[]>(`/api/products/${id}/bought-together`, { params: { limit } });
  }

  /** Cached for the session; admins call {@link invalidateCategories} after editing. */
  categories(): Observable<Category[]> {
    this.categories$ ??= this.http.get<Category[]>('/api/categories').pipe(shareReplay(1));
    return this.categories$;
  }

  invalidateCategories(): void {
    this.categories$ = undefined;
  }

  shop(slug: string): Observable<Shop> {
    return this.http.get<Shop>(`/api/shops/${slug}`);
  }

  reviews(productId: number, page = 0, size = 5): Observable<Page<Review>> {
    return this.http.get<Page<Review>>(`/api/products/${productId}/reviews`, { params: { page, size } });
  }

  reviewEligibility(productId: number): Observable<ReviewEligibility> {
    return this.http.get<ReviewEligibility>(`/api/products/${productId}/reviews/eligibility`);
  }

  addReview(productId: number, rating: number, comment: string): Observable<Review> {
    return this.http.post<Review>(`/api/products/${productId}/reviews`, { rating, comment });
  }
}
