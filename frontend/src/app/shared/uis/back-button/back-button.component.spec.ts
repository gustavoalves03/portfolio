import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BackButtonComponent } from './back-button.component';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';

describe('BackButtonComponent', () => {
  let fixture: ComponentFixture<BackButtonComponent>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;
  let history: jasmine.SpyObj<NavigationHistoryService>;

  beforeEach(async () => {
    location = jasmine.createSpyObj<Location>('Location', ['back']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    history = jasmine.createSpyObj<NavigationHistoryService>('NavigationHistoryService', ['hasInternalHistory']);

    await TestBed.configureTestingModule({
      imports: [
        BackButtonComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
        { provide: NavigationHistoryService, useValue: history },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BackButtonComponent);
  });

  it('calls Location.back() when internal history exists', () => {
    history.hasInternalHistory.and.returnValue(true);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLElement).click();
    expect(location.back).toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('navigates to fallbackUrl when no internal history', () => {
    history.hasInternalHistory.and.returnValue(false);
    fixture.componentRef.setInput('fallbackUrl', '/pro/manage');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/pro/manage']);
    expect(location.back).not.toHaveBeenCalled();
  });

  it('defaults fallbackUrl to "/"', () => {
    history.hasInternalHistory.and.returnValue(false);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('renders label by default and hides it when showLabel is false', () => {
    history.hasInternalHistory.and.returnValue(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('span')).not.toBeNull();

    fixture.componentRef.setInput('showLabel', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('span')).toBeNull();
  });
});
