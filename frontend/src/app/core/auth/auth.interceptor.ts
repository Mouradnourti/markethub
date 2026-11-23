import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const PUBLIC_AUTH_URLS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh'];

function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}

/**
 * Adds the JWT to API calls. On a 401 it refreshes the token once and replays the request;
 * if the refresh fails the session is closed and the user is sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isApi = request.url.startsWith('/api/');
  const isAuthCall = PUBLIC_AUTH_URLS.some((url) => request.url.startsWith(url));
  if (!isApi || isAuthCall) {
    return next(request);
  }

  return next(withToken(request, auth.accessToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !auth.accessToken) {
        return throwError(() => error);
      }
      return auth.refreshAccessToken().pipe(
        switchMap((token) => next(withToken(request, token))),
        catchError((refreshError: unknown) => {
          auth.logout(false);
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
