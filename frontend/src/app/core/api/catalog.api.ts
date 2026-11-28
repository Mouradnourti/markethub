import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { SILENT_ERRORS } from '../http/error.interceptor';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import {
  Category,
  Facets,
  Offer,
  Page,
  Product,
  ProductQuery,
  ProductSummary,
  Review,
  ReviewEligibility,
  ReviewSort,
  ReviewSummary,
  Shop,
  SmartSearchResult,
  Suggestions,
} from '../models/api.models';

function silent(): HttpContext {
  return new HttpContext().set(SILENT_ERRORS, true);
}

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

  suggest(q: string): Observable<Suggestions> {
    return this.http.get<Suggestions>('/api/products/suggest', { params: { q }, context: silent() });
  }

  facets(query: ProductQuery): Observable<Facets> {
    const { sort, page, size, ...filters } = query;
    return this.http.get<Facets>('/api/products/facets', { params: toParams(filters), context: silent() });
  }

  smartSearch(q: string, limit = 8): Observable<SmartSearchResult> {
    return this.http.get<SmartSearchResult>('/api/products/smart-search', { params: { q, limit }, context: silent() });
  }

  offers(shopId: number): Observable<Offer[]> {
    return this.http.get<Offer[]>(`/api/shops/${shopId}/offers`, { context: silent() });
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

  reviews(productId: number, page = 0, size = 6, sort: ReviewSort = 'HELPFUL', rating?: number): Observable<Page<Review>> {
    return this.http.get<Page<Review>>(`/api/products/${productId}/reviews`, {
      params: toParams({ page, size, sort, rating }),
    });
  }

  reviewSummary(productId: number): Observable<ReviewSummary> {
    return this.http.get<ReviewSummary>(`/api/products/${productId}/reviews/summary`, { context: silent() });
  }

  voteHelpful(productId: number, reviewId: number, helpful: boolean): Observable<Review> {
    const url = `/api/products/${productId}/reviews/${reviewId}/helpful`;
    return helpful ? this.http.post<Review>(url, null) : this.http.delete<Review>(url);
  }

  addReviewPhoto(productId: number, reviewId: number, file: File): Observable<Review> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Review>(`/api/products/${productId}/reviews/${reviewId}/photos`, form);
  }

  reviewEligibility(productId: number): Observable<ReviewEligibility> {
    return this.http.get<ReviewEligibility>(`/api/products/${productId}/reviews/eligibility`);
  }

  addReview(productId: number, rating: number, comment: string): Observable<Review> {
    return this.http.post<Review>(`/api/products/${productId}/reviews`, { rating, comment });
  }
}
