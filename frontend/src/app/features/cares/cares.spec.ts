import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Cares } from './cares';

describe('Cares', () => {
  let component: Cares;
  let fixture: ComponentFixture<Cares>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Cares]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Cares);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
