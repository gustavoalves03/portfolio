import { Pipe, PipeTransform } from '@angular/core';
import { formatDateTime, type DateInput } from '../../core/utils/date-format';

@Pipe({ name: 'appDateTime', standalone: true, pure: true })
export class AppDateTimePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatDateTime(value);
  }
}
