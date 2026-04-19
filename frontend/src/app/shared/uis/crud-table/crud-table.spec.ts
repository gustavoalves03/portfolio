import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { CrudTable } from './crud-table';

describe('CrudTable', () => {
  let component: CrudTable;
  let fixture: ComponentFixture<CrudTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CrudTable,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    })
    .compileComponents();

    fixture = TestBed.createComponent(CrudTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('dataSource', []);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
