import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { WebSocketService } from './websocket.service';
import { AuthService } from '../../../core/auth/auth.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { NotificationResponse } from '../models/notification.model';

describe('WebSocketService', () => {
  let service: WebSocketService;
  let auth: jasmine.SpyObj<Pick<AuthService, 'getToken'>>;
  let activateSpy: jasmine.Spy;
  let lastClient: any;

  const apiUrl = 'http://localhost:8080';

  beforeEach(() => {
    // Spy on Client.prototype.activate to prevent real WebSocket creation and
    // capture the constructed Client instance (with its config set by the ctor).
    lastClient = null;
    activateSpy = spyOn(Client.prototype, 'activate').and.callFake(function (
      this: any
    ) {
      lastClient = this;
    });

    auth = jasmine.createSpyObj<Pick<AuthService, 'getToken'>>('AuthService', ['getToken']);
    auth.getToken.and.returnValue('test-jwt-token');

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        WebSocketService,
        { provide: AuthService, useValue: auth },
        { provide: API_BASE_URL, useValue: apiUrl },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });

    service = TestBed.inject(WebSocketService);
  });

  it('connect() creates a STOMP Client with ws:// URL ending in /ws and Bearer token header', () => {
    service.connect();

    expect(activateSpy).toHaveBeenCalled();
    expect(lastClient).not.toBeNull();
    expect(lastClient.brokerURL).toBe('ws://localhost:8080/ws');
    expect(lastClient.connectHeaders.Authorization).toBe('Bearer test-jwt-token');
  });

  it('connect() does not create a client when no token is available', () => {
    auth.getToken.and.returnValue(null);

    service.connect();

    expect(activateSpy).not.toHaveBeenCalled();
  });

  it('incoming STOMP message is parsed and emitted on notification$', (done) => {
    const payload: NotificationResponse = {
      id: 42,
      type: 'BOOKING_CREATED',
      category: 'INFO',
      title: 'Nouveau rendez-vous',
      message: 'Marie Claire - 14h30',
      referenceId: 100,
      referenceType: 'BOOKING',
      read: false,
      tenantSlug: 'salon-a',
      createdAt: '2026-04-18T14:30:00Z',
    };

    service.notification$.subscribe((n) => {
      expect(n).toEqual(payload);
      done();
    });

    service.connect();

    // Stub subscribe so the onConnect handler can register a callback we can invoke.
    let capturedCallback: ((msg: { body: string }) => void) | null = null;
    lastClient.subscribe = (destination: string, cb: (msg: { body: string }) => void) => {
      expect(destination).toBe('/user/queue/notifications');
      capturedCallback = cb;
      return { id: 'sub-1', unsubscribe: () => {} } as any;
    };

    // Simulate STOMP broker calling onConnect, which subscribes.
    lastClient.onConnect();

    expect(capturedCallback).not.toBeNull();
    // Simulate an incoming message frame with a JSON body.
    capturedCallback!({ body: JSON.stringify(payload) });
  });
});
