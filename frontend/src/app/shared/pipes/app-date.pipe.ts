import { Pipe, PipeTransform } from '@angular/core';
import { formatDate, type DateInput } from '../../core/utils/date-format';

@Pipe({ name: 'appDate', standalone: true, pure: true })
export class AppDatePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatDate(value);
  }
}
