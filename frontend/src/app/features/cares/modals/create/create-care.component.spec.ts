import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateCare } from './create-care.component';

describe('Create', () => {
  let component: CreateCare;
  let fixture: ComponentFixture<CreateCare>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateCare]
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
