import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from './i18n.service';

/** {{ 'Add to cart' | t }} or {{ 'Hi, {name}' | t: { name: user.firstName } }}. Pure: the language only changes on reload. */
@Pipe({ name: 't' })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string, params?: Record<string, string | number | null | undefined>): string {
    return this.i18n.t(key, params);
  }
}
