import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from '../models/api.models';
import { Notifier } from '../ui/notifier.service';

/** Set to true on a request whose errors the caller displays itself. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

export function problemMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as ProblemDetail | null;
    if (problem?.errors) {
      return Object.entries(problem.errors)
        .map(([field, message]) => `${field}: ${message}`)
        .join(' · ');
    }
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.status === 0) {
      return 'The server cannot be reached. Check your connection.';
    }
  }
  return 'Something went wrong, please try again.';
}

/** Shows backend ProblemDetail messages in a snackbar. 401s are handled by the auth interceptor. */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifier = inject(Notifier);
  return next(request).pipe(
    catchError((error: unknown) => {
      const silent = request.context.get(SILENT_ERRORS);
      if (!silent && error instanceof HttpErrorResponse && error.status !== 401) {
        notifier.error(problemMessage(error));
      }
      return throwError(() => error);
    }),
  );
};
