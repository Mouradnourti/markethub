import { Pipe, PipeTransform, inject } from '@angular/core';
import { ConfigService } from '../../core/config/config.service';
import { I18nService } from '../../core/i18n/i18n.service';

/** Formats an amount in the platform currency, e.g. 1234.5 → "€1,234.50". */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly config = inject(ConfigService);
  private readonly locale = inject(I18nService).intlLocale;
  private formatter?: Intl.NumberFormat;
  private formatterCurrency?: string;

  transform(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }
    const currency = this.config.currencyCode;
    if (!this.formatter || this.formatterCurrency !== currency) {
      this.formatter = new Intl.NumberFormat(this.locale, { style: 'currency', currency });
      this.formatterCurrency = currency;
    }
    return this.formatter.format(value);
  }
}
