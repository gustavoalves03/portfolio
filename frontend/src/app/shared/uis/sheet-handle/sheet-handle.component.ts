import { Component } from '@angular/core';
import { BottomSheetDragDirective } from './bottom-sheet-drag.directive';

@Component({
  selector: 'app-sheet-handle',
  standalone: true,
  imports: [BottomSheetDragDirective],
  template: `<div class="sheet-handle" appBottomSheetDrag aria-label="Close" role="presentation"></div>`,
  styles: [`:host { display: block; }`],
})
export class SheetHandleComponent {}
