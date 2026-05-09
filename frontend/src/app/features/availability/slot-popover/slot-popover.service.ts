import { ElementRef, Injectable, inject } from '@angular/core';
import { ConnectedPosition, Overlay, OverlayConfig } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { MatDialog } from '@angular/material/dialog';
import { Observable, Subject, take } from 'rxjs';
import {
  SlotPopoverComponent,
  SlotPopoverData,
  SlotPopoverResult,
} from './slot-popover.component';
import { bottomSheetConfig } from '../../../shared/uis/sheet-handle/bottom-sheet.config';

@Injectable({ providedIn: 'root' })
export class SlotPopoverService {
  private readonly overlay = inject(Overlay);
  private readonly dialog = inject(MatDialog);

  /**
   * Opens the popover positioned next to `anchor` on PC, or as a bottom-sheet
   * on mobile. Returns a stream emitting once with the user's choice and then
   * completing.
   */
  open(
    data: SlotPopoverData,
    anchor: ElementRef<HTMLElement> | null,
    isDesktop: boolean,
  ): Observable<SlotPopoverResult> {
    if (isDesktop && anchor) {
      return this.openOverlay(data, anchor);
    }
    return this.openDialog(data);
  }

  private openOverlay(
    data: SlotPopoverData,
    anchor: ElementRef<HTMLElement>,
  ): Observable<SlotPopoverResult> {
    const positions: ConnectedPosition[] = [
      { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
      { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
    ];
    const positionStrategy = this.overlay
      .position()
      .flexibleConnectedTo(anchor)
      .withPositions(positions)
      .withPush(true);

    const config = new OverlayConfig({
      positionStrategy,
      scrollStrategy: this.overlay.scrollStrategies.reposition(),
      hasBackdrop: true,
      backdropClass: 'slot-popover-backdrop',
    });
    const overlayRef = this.overlay.create(config);
    const portal = new ComponentPortal(SlotPopoverComponent);
    const ref = overlayRef.attach(portal);
    ref.setInput('data', data);

    const result$ = new Subject<SlotPopoverResult>();

    const closeWith = (r: SlotPopoverResult) => {
      if (result$.closed) return;
      result$.next(r);
      result$.complete();
      overlayRef.dispose();
    };

    ref.instance.confirm.subscribe((r) => closeWith(r));
    overlayRef.backdropClick().subscribe(() => closeWith({ action: 'cancel' }));
    // If the overlay is disposed for any other reason, complete result$ silently.
    overlayRef.detachments().subscribe(() => {
      if (!result$.closed) result$.complete();
    });

    return result$.asObservable().pipe(take(1));
  }

  private openDialog(data: SlotPopoverData): Observable<SlotPopoverResult> {
    const ref = this.dialog.open<SlotPopoverComponent, SlotPopoverData, SlotPopoverResult>(
      SlotPopoverComponent,
      bottomSheetConfig({ data, panelClass: 'slot-popover-dialog' }),
    );
    ref.componentRef!.setInput('data', data);

    const result$ = new Subject<SlotPopoverResult>();
    ref.componentInstance.confirm.subscribe((r) => {
      result$.next(r);
      result$.complete();
      ref.close();
    });
    ref.afterClosed().subscribe(() => {
      if (!result$.closed) {
        result$.next({ action: 'cancel' });
        result$.complete();
      }
    });
    return result$.asObservable().pipe(take(1));
  }
}
