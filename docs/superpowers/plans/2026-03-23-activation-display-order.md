# Story 2.5 — Activation/Deactivation & Display Order

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add quick status toggle (active/inactive) and drag-and-drop reordering for cares in the pro management page.

**Architecture:** Backend: add `displayOrder` column to Care entity, add `PATCH /api/care/{id}/status` and `PATCH /api/care/reorder` endpoints. Frontend: extend CrudTable with `toggle` column type and `reorderable` mode (CDK DragDrop), wire into CaresComponent.

**Tech Stack:** Spring Boot 3.5.4 / Java 21, Angular 20 (standalone, zoneless, signals), NgRx SignalStore, Angular Material, CDK DragDrop, Transloco i18n

**Spec:** Design validated in brainstorming session (Story 2.5).

---

## Chunk 1: Backend

### Task 1: Add `displayOrder` field to Care entity

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/domain/Care.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/web/dto/CareResponse.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/web/mapper/CareMapper.java`

- [ ] **Step 1: Add displayOrder field to Care entity**

Add after the `duration` field in `Care.java`:

```java
@Column(name = "display_order")
private Integer displayOrder;
```

Lombok `@Getter @Setter` covers the accessor.

- [ ] **Step 2: Add displayOrder to CareResponse**

Change `CareResponse.java` to include `displayOrder`:

```java
public record CareResponse(
        Long id,
        String name,
        Integer price,
        String description,
        Integer duration,
        CareStatus status,
        Long categoryId,
        Integer displayOrder,
        List<CareImageDto> images
) {}
```

- [ ] **Step 3: Update CareMapper to map displayOrder**

In `CareMapper.java`, update `toResponse()` to include `care.getDisplayOrder()` in the constructor call.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/care/domain/Care.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/web/dto/CareResponse.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/web/mapper/CareMapper.java
git commit -m "feat: add displayOrder field to Care entity and response DTO"
```

---

### Task 2: Add status toggle endpoint

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/care/web/dto/StatusRequest.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/app/CareService.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/web/CareController.java`

- [ ] **Step 1: Create StatusRequest DTO**

```java
package com.fleurdecoquillage.app.care.web.dto;

import com.fleurdecoquillage.app.care.domain.CareStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull CareStatus status) {}
```

- [ ] **Step 2: Add toggleStatus method to CareService**

Add to `CareService.java`:

```java
@Transactional
public CareResponse toggleStatus(Long id, CareStatus status) {
    if (status != CareStatus.ACTIVE && status != CareStatus.INACTIVE) {
        throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
    }
    Care c = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Care not found: " + id));
    c.setStatus(status);
    return CareMapper.toResponse(repo.save(c));
}
```

- [ ] **Step 3: Add PATCH endpoint to CareController**

Add to `CareController.java`:

```java
@PatchMapping("/{id}/status")
public CareResponse toggleStatus(@PathVariable Long id, @RequestBody @Valid StatusRequest req) {
    return service.toggleStatus(id, req.status());
}
```

Import `StatusRequest` at the top.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/care/web/dto/StatusRequest.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/app/CareService.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/web/CareController.java
git commit -m "feat: add PATCH /api/care/{id}/status toggle endpoint"
```

---

### Task 3: Add reorder endpoint

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/care/web/dto/ReorderRequest.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/app/CareService.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/web/CareController.java`

- [ ] **Step 1: Create ReorderRequest DTO**

```java
package com.fleurdecoquillage.app.care.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderRequest(@NotEmpty List<Long> orderedIds) {}
```

- [ ] **Step 2: Add reorder method to CareService**

Add to `CareService.java`:

```java
@Transactional
public void reorder(List<Long> orderedIds) {
    for (int i = 0; i < orderedIds.size(); i++) {
        Care c = repo.findById(orderedIds.get(i))
                .orElseThrow(() -> new IllegalArgumentException("Care not found: " + orderedIds.get(i)));
        c.setDisplayOrder(i);
        repo.save(c);
    }
}
```

- [ ] **Step 3: Add PATCH reorder endpoint to CareController**

Add to `CareController.java`:

```java
@PatchMapping("/reorder")
@ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
public void reorder(@RequestBody @Valid ReorderRequest req) {
    service.reorder(req.orderedIds());
}
```

Import `ReorderRequest` and `ResponseStatus` / `HttpStatus` at the top. **Important:** place this method BEFORE `@PatchMapping("/{id}/status")` to avoid path conflict (`/reorder` must be matched before `/{id}`).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/care/web/dto/ReorderRequest.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/app/CareService.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/web/CareController.java
git commit -m "feat: add PATCH /api/care/reorder endpoint"
```

---

### Task 4: Update list query to sort by displayOrder

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/repo/CareRepository.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/care/app/CareService.java`

- [ ] **Step 1: Add ordered findAll to CareRepository**

Add to `CareRepository.java`:

```java
@Query("SELECT c FROM Care c ORDER BY COALESCE(c.displayOrder, 999999) ASC, c.id ASC")
List<Care> findAllOrdered();
```

Import `java.util.List` and `org.springframework.data.jpa.repository.Query`.

- [ ] **Step 2: Add listOrdered method to CareService**

Add to `CareService.java`:

```java
@Transactional(readOnly = true)
public List<CareResponse> listOrdered() {
    return repo.findAllOrdered().stream().map(CareMapper::toResponse).toList();
}
```

Import `java.util.List`.

- [ ] **Step 3: Add GET endpoint for ordered list to CareController**

Add to `CareController.java`:

```java
@GetMapping("/ordered")
public List<CareResponse> listOrdered() {
    return service.listOrdered();
}
```

Place BEFORE the `@GetMapping("/{id}")` to avoid path conflict.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/care/repo/CareRepository.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/app/CareService.java \
       backend/src/main/java/com/fleurdecoquillage/app/care/web/CareController.java
git commit -m "feat: add ordered care list endpoint sorted by displayOrder"
```

---

## Chunk 2: Frontend — CrudTable enhancements

### Task 5: Add toggle column type to CrudTable

**Files:**
- Modify: `frontend/src/app/shared/uis/crud-table/crud-table.models.ts`
- Modify: `frontend/src/app/shared/uis/crud-table/crud-table.ts`
- Modify: `frontend/src/app/shared/uis/crud-table/crud-table.html`

- [ ] **Step 1: Extend TableColumn with toggleCallback**

In `crud-table.models.ts`, add to `TableColumn`:

```typescript
/** Callback when toggle column value changes */
toggleCallback?: (item: any, newValue: boolean) => void;
```

- [ ] **Step 2: Add MatSlideToggleModule import to CrudTable**

In `crud-table.ts`, add `MatSlideToggleModule` to imports:

```typescript
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
```

Add it to the `imports` array in the `@Component` decorator.

- [ ] **Step 3: Add toggle column rendering in template**

In `crud-table.html`, inside the `@else` block (normal columns), replace the `<td>` cell:

```html
<td mat-cell *matCellDef="let element" (click)="$event.stopPropagation()">
  @if (getColumnConfig(columnKey)?.type === 'toggle') {
    <mat-slide-toggle
      [checked]="!!resolveCellValue(columnKey, element)"
      (change)="getColumnConfig(columnKey)?.toggleCallback?.(element, $event.checked)"
      (click)="$event.stopPropagation()"
    ></mat-slide-toggle>
  } @else {
    {{ resolveCellValue(columnKey, element) }}
  }
</td>
```

- [ ] **Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/crud-table/crud-table.models.ts \
       frontend/src/app/shared/uis/crud-table/crud-table.ts \
       frontend/src/app/shared/uis/crud-table/crud-table.html
git commit -m "feat: add toggle column type to CrudTable"
```

---

### Task 6: Add reorderable mode to CrudTable

**Files:**
- Modify: `frontend/src/app/shared/uis/crud-table/crud-table.ts`
- Modify: `frontend/src/app/shared/uis/crud-table/crud-table.html`
- Modify: `frontend/src/app/shared/uis/crud-table/crud-table.scss`

- [ ] **Step 1: Add CDK DragDrop imports and inputs to CrudTable**

In `crud-table.ts`, add imports and new input/output:

```typescript
import { CdkDragDrop, CdkDropList, CdkDrag, moveItemInArray } from '@angular/cdk/drag-drop';
```

Add to `imports` array: `CdkDropList, CdkDrag`

Add new input and output:

```typescript
reorderable = input<boolean>(false);
reorder = output<any[]>();
```

Add handler method:

```typescript
onDrop(event: CdkDragDrop<any[]>) {
  const data = [...this.dataSource()];
  moveItemInArray(data, event.previousIndex, event.currentIndex);
  this.reorder.emit(data);
}
```

- [ ] **Step 2: Update template for drag-drop**

In `crud-table.html`, wrap the table body with drag-drop directives. Update the `<table>` tag:

```html
<table mat-table [dataSource]="dataSource()" class="mat-elevation-z2 demo-table"
       [cdkDropList]="reorderable() ? true : null"
       [cdkDropListDisabled]="!reorderable()"
       (cdkDropListDropped)="onDrop($event)">
```

Update the data row `<tr>`:

```html
<tr mat-row *matRowDef="let row; columns: columnKeys();"
    (click)="onRowClick(row)"
    class="clickable-row"
    [cdkDrag]="reorderable()"
    [cdkDragDisabled]="!reorderable()"></tr>
```

Add a drag handle column. In the `columnKeys` computed, prepend `'drag'` when reorderable:

In `crud-table.ts`, update `columnKeys`:

```typescript
protected readonly columnKeys = computed(() => {
  const cols = this.columns();
  const displayedCols = this.displayedColumns();

  if (cols && cols.length > 0) {
    const keys = cols.map(c => c.key);
    if (this.actions().length > 0) {
      keys.push('actions');
    }
    if (this.reorderable()) {
      keys.unshift('drag');
    }
    return keys;
  }

  if (displayedCols && displayedCols.length > 0) {
    return displayedCols;
  }

  return [];
});
```

Add drag handle column in template, inside the `@for (columnKey of columnKeys())` before the actions block:

```html
@if (columnKey === 'drag') {
  <ng-container matColumnDef="drag">
    <th mat-header-cell *matHeaderCellDef class="drag-column"></th>
    <td mat-cell *matCellDef="let element" class="drag-column">
      <mat-icon class="drag-handle" cdkDragHandle>drag_indicator</mat-icon>
    </td>
  </ng-container>
} @else if (columnKey === 'actions') {
```

- [ ] **Step 3: Add drag-drop styles**

Add to `crud-table.scss`:

```scss
.drag-column {
  width: 40px;
  padding: 0 4px !important;
}

.drag-handle {
  cursor: grab;
  color: #bbb;
  font-size: 20px;

  &:active {
    cursor: grabbing;
  }
}

.cdk-drag-preview {
  background: white;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
}

.cdk-drag-placeholder {
  opacity: 0.3;
}

.cdk-drag-animating {
  transition: transform 200ms ease;
}
```

- [ ] **Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/crud-table/crud-table.ts \
       frontend/src/app/shared/uis/crud-table/crud-table.html \
       frontend/src/app/shared/uis/crud-table/crud-table.scss
git commit -m "feat: add reorderable drag-and-drop mode to CrudTable"
```

---

## Chunk 3: Frontend — Integration

### Task 7: Add i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French keys**

Add inside the `"cares"` block:

```json
"toggleSuccess": "Statut mis à jour",
"reorderSuccess": "Ordre mis à jour"
```

- [ ] **Step 2: Add English keys**

Add inside the `"cares"` block:

```json
"toggleSuccess": "Status updated",
"reorderSuccess": "Order updated"
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add i18n keys for toggle and reorder"
```

---

### Task 8: Add toggle and reorder methods to CaresService

**Files:**
- Modify: `frontend/src/app/features/cares/services/cares.service.ts`
- Modify: `frontend/src/app/features/cares/models/cares.model.ts`

- [ ] **Step 1: Add displayOrder to Care model**

In `cares.model.ts`, add to `Care` interface:

```typescript
displayOrder?: number;
```

- [ ] **Step 2: Add toggle and reorder methods to CaresService**

Add to `CaresService`:

```typescript
toggleStatus(id: number, status: CareStatus): Observable<Care> {
  return this.http.patch<Care>(
    `${this.apiBaseUrl}${this.basePath}/${id}/status`,
    { status }
  ).pipe(map(care => this.transformCareImageUrls(care)));
}

reorder(orderedIds: number[]): Observable<void> {
  return this.http.patch<void>(
    `${this.apiBaseUrl}${this.basePath}/reorder`,
    { orderedIds }
  );
}

listOrdered(): Observable<Care[]> {
  return this.http.get<Care[]>(
    `${this.apiBaseUrl}${this.basePath}/ordered`
  ).pipe(
    map(cares => cares.map(care => this.transformCareImageUrls(care)))
  );
}
```

Add `HttpClient` injection if not already present (it's inherited from `BaseCrudService`, access via `this.http`).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/cares/services/cares.service.ts \
       frontend/src/app/features/cares/models/cares.model.ts
git commit -m "feat: add toggleStatus, reorder, listOrdered to CaresService"
```

---

### Task 9: Add toggle and reorder methods to CaresStore

**Files:**
- Modify: `frontend/src/app/features/cares/store/cares.store.ts`

- [ ] **Step 1: Add store methods**

Add these methods inside `withMethods(...)`:

```typescript
toggleCareStatus: rxMethod<{ id: number; status: CareStatus }>(
  pipe(
    exhaustMap(({ id, status }) =>
      caresGateway.toggleStatus(id, status).pipe(
        tap((updatedCare) =>
          patchState(store, {
            cares: store.cares().map((care) => (care.id === id ? updatedCare : care)),
          })
        ),
        catchError((err) => {
          patchState(store, setError(extractErrorMessage(err, 'Erreur lors du changement de statut')));
          return EMPTY;
        })
      )
    )
  )
),
reorderCares: rxMethod<number[]>(
  pipe(
    exhaustMap((orderedIds) =>
      caresGateway.reorder(orderedIds).pipe(
        catchError((err) => {
          patchState(store, setError(extractErrorMessage(err, 'Erreur lors du réordonnancement')));
          return EMPTY;
        })
      )
    )
  )
),
```

- [ ] **Step 2: Update getCares to use ordered endpoint**

Change the `getCares` method to call `listOrdered()` instead of `list()`:

```typescript
getCares: rxMethod<void>(
  pipe(
    tap(() => patchState(store, setPending())),
    switchMap(() =>
      caresGateway.listOrdered().pipe(
        tap((cares) => patchState(store, { cares }, setFulfilled())),
        catchError((err) => {
          patchState(store, setError(extractErrorMessage(err, 'Erreur de chargement des soins')));
          return EMPTY;
        })
      )
    )
  )
),
```

- [ ] **Step 3: Remove the ACTIVE-only filter from availableCares**

Change the `availableCares` computed to return all cares (both ACTIVE and INACTIVE), since we now want to show them all with a toggle:

```typescript
availableCares: computed(() => store.cares()),
```

- [ ] **Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/cares/store/cares.store.ts
git commit -m "feat: add toggleCareStatus, reorderCares to CaresStore"
```

---

### Task 10: Wire toggle and reorder into CaresComponent

**Files:**
- Modify: `frontend/src/app/features/cares/cares.component.ts`

- [ ] **Step 1: Update status column to toggle type**

In the `columns` signal, replace the status column:

```typescript
{
  key: 'status',
  headerKey: 'cares.columns.status',
  type: 'toggle',
  align: 'center',
  valueGetter: (care: Care) => care.status === CareStatus.ACTIVE,
  toggleCallback: (care: Care, checked: boolean) => {
    this.store.toggleCareStatus({
      id: care.id,
      status: checked ? CareStatus.ACTIVE : CareStatus.INACTIVE,
    });
    this.snackBar.open(this.i18n.translate('cares.toggleSuccess'), 'OK', { duration: 2000 });
  },
},
```

- [ ] **Step 2: Add onReorder method**

Add to `CaresComponent`:

```typescript
onReorder(reorderedCares: Care[]): void {
  const orderedIds = reorderedCares.map(c => c.id);
  this.store.reorderCares(orderedIds);
  this.snackBar.open(this.i18n.translate('cares.reorderSuccess'), 'OK', { duration: 2000 });
}
```

- [ ] **Step 3: Enable reorderable on CrudTable in template**

In `cares.component.html`, add `[reorderable]="true"` and `(reorder)="onReorder($event)"` to the `<crud-table>`:

```html
<crud-table
  [dataSource]="filteredCares()"
  [columns]="columns()"
  [actions]="actions()"
  [title]="('cares.title' | transloco)"
  [emptyMessage]="('table.empty' | transloco)"
  [searchPlaceholder]="('table.search.placeholder' | transloco)"
  [loading]="store.isPending()"
  [errorMessage]="store.error()"
  [reorderable]="true"
  (addItem)="onAddCare()"
  (rowClick)="onViewCareDetails($event)"
  (reorder)="onReorder($event)"
></crud-table>
```

- [ ] **Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/cares/cares.component.ts \
       frontend/src/app/features/cares/cares.component.html
git commit -m "feat: integrate status toggle and drag-drop reorder into cares page"
```

---

## Chunk 4: Verification

### Task 11: Final verification

- [ ] **Step 1: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Verify translations**

Check that `cares.toggleSuccess` and `cares.reorderSuccess` exist in both `fr.json` and `en.json`.

- [ ] **Step 3: Verify no import issues**

Check that `CdkDragDrop`, `CdkDropList`, `CdkDrag`, `moveItemInArray` are properly imported from `@angular/cdk/drag-drop` in CrudTable, and that `MatSlideToggleModule` is imported.
