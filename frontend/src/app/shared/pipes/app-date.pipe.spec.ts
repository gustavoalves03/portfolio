import { AppDatePipe } from './app-date.pipe';
import { AppDateTimePipe } from './app-datetime.pipe';

describe('AppDatePipe', () => {
  const pipe = new AppDatePipe();

  it('delegates to formatDate', () => {
    expect(pipe.transform('2026-04-18')).toBe('18/04/2026');
    expect(pipe.transform(null)).toBe('');
  });
});

describe('AppDateTimePipe', () => {
  const pipe = new AppDateTimePipe();

  it('delegates to formatDateTime', () => {
    expect(pipe.transform('2026-04-18T14:30:00')).toBe('18/04/2026 14:30');
    expect(pipe.transform(null)).toBe('');
  });
});
