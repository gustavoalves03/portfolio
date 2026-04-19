import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { CreateCare } from './create-care.component';

describe('Create', () => {
  let component: CreateCare;
  let fixture: ComponentFixture<CreateCare>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateCare],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MAT_DIALOG_DATA, useValue: { categories: [] } },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(CreateCare);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
