import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeAr from '@angular/common/locales/ar';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { ConfigService } from './core/config/config.service';
import { errorInterceptor } from './core/http/error.interceptor';
import { I18nService, storedLanguage } from './core/i18n/i18n.service';

registerLocaleData(localeFr);
registerLocaleData(localeAr);
const ANGULAR_LOCALES = { en: 'en-US', fr: 'fr', ar: 'ar' } as const;

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
    ),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // Dates follow the chosen language; switching language reloads the app (see I18nService).
    { provide: LOCALE_ID, useFactory: () => ANGULAR_LOCALES[storedLanguage()] },
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline', subscriptSizing: 'dynamic' } },
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
      return Promise.all([inject(I18nService).load(), inject(ConfigService).load(), inject(AuthService).init()]);
    }),
  ],
};
