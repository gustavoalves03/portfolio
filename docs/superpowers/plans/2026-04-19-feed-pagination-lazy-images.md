# Feed Pagination + Lazy Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paginate the public salon posts endpoint to avoid unbounded loads and add native lazy-loading on all post images.

**Architecture:** New paged repository method + new service method + updated `PublicSalonController` endpoint (no change to the pro `/api/pro/posts` path). Frontend `listPublic` signature updated to accept page/size, `SalonPostsViewerComponent` tracks state and loads next page when the snap-scroll reaches the last item. `loading="lazy"` added on every `<img>` in salon + home feed viewers.

**Tech Stack:** Spring Boot 3.5, JPA Pageable, Angular 20 standalone zoneless, native HTML lazy-loading.

**Spec:** `docs/superpowers/specs/2026-04-19-feed-pagination-lazy-images-design.md`

---

## Key details verified

- Current endpoint: `GET /api/salon/{slug}/posts` → `PublicSalonController.listPosts(slug)` → `postService.listAll()` → `postRepo.findAllByOrderByCreatedAtDesc()` returning `List<Post>`.
- `postService.listAll()` is also used by `PostController.list()` (pro). We DON'T change it to avoid impacting the pro path.
- Frontend: `PostsService.listPublic(slug): Observable<PostResponse[]>` (plain list, not Page).
- `SalonPostsViewerComponent` already has `onScroll()` reacting to snap-scroll position — it's the natural hook for infinite scroll.

## File Structure

**Backend modified (3 files):**
- `backend/src/main/java/com/prettyface/app/post/repo/PostRepository.java`
- `backend/src/main/java/com/prettyface/app/post/app/PostService.java`
- `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`

**Frontend modified (3 files):**
- `frontend/src/app/features/posts/posts.service.ts`
- `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts` (state + onScroll + template `loading="lazy"`)
- `frontend/src/app/features/posts/recent-posts-viewer/recent-posts-viewer.component.ts` (only `loading="lazy"` on images)

**Test files (new or modified):**
- `backend/src/test/java/com/prettyface/app/post/app/PostServiceTests.java` (new or extend)
- `backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerTests.java` (create/extend)
- `frontend/src/app/features/posts/posts.service.spec.ts` (create)

Each task below is self-contained and ends with a commit.

---

## Task 1: Backend — repository paginated method

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/post/repo/PostRepository.java`

- [ ] **Step 1: Add the paged method**

Open the file. Current content has one method:

```java
List<Post> findAllByOrderByCreatedAtDesc();
```

Add below it:

```java
Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
```

Add imports at the top of the file:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

Keep the existing `findAllByOrderByCreatedAtDesc()` (non-paged) intact — it's used by `listAll()` on the pro path.

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && ./mvnw compile 2>&1 | tail -10`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/post/repo/PostRepository.java
git commit -m "feat(feed): add paged post query"
```

---

## Task 2: Backend — service method + test

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/post/app/PostService.java`
- Create/Modify: `backend/src/test/java/com/prettyface/app/post/app/PostServiceTests.java`

- [ ] **Step 1: Check if the test file exists**

Run: `ls /Users/Gustavo.alves/Documents/personal/portfolio/backend/src/test/java/com/prettyface/app/post/app/ 2>/dev/null`

If `PostServiceTests.java` exists, open it; if not, create.

- [ ] **Step 2: Write the failing test**

If creating fresh, use this full file:

```java
// backend/src/test/java/com/prettyface/app/post/app/PostServiceTests.java
package com.prettyface.app.post.app;

import com.prettyface.app.post.domain.Post;
import com.prettyface.app.post.domain.PostType;
import com.prettyface.app.post.repo.PostRepository;
import com.prettyface.app.post.web.dto.PostResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTests {

    @Mock PostRepository postRepo;
    @InjectMocks PostService service;

    @Test
    void listPublicPaged_delegatesToRepoAndMapsToResponse() {
        Post p = Post.builder()
                .id(1L)
                .type(PostType.PHOTO)
                .caption("hello")
                .imagePath("/uploads/posts/abc.jpg")
                .createdAt(LocalDateTime.now())
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepo.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), pageable, 1));

        Page<PostResponse> result = service.listPublicPaged(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        verify(postRepo).findAllByOrderByCreatedAtDesc(pageable);
    }
}
```

If appending to an existing file, add only the `@Test` method and needed imports.

- [ ] **Step 3: Run test — expect failure**

Run: `./mvnw test -Dtest=PostServiceTests 2>&1 | tail -15`

Expected: FAIL with `listPublicPaged not defined` or similar compile error.

- [ ] **Step 4: Implement the service method**

In `backend/src/main/java/com/prettyface/app/post/app/PostService.java`, after the existing `listAll()` method, add:

```java
public Page<PostResponse> listPublicPaged(Pageable pageable) {
    return postRepo.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
}
```

Add imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

The `toResponse` helper already exists (used by `listAll`). If its access is private, make sure it's reachable — keep it private and use the method reference `this::toResponse` as shown.

If the existing `listAll()` builds a `PostResponse` via a different path (e.g. inline lambda), extract the mapping into a private `toResponse(Post)` method and reuse it. Read the current file before editing.

- [ ] **Step 5: Run test — expect pass**

Run: `./mvnw test -Dtest=PostServiceTests 2>&1 | tail -15`

Expected: 1 passed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/post/app/PostService.java \
        backend/src/test/java/com/prettyface/app/post/app/PostServiceTests.java
git commit -m "feat(feed): paged listPublicPaged service method"
```

---

## Task 3: Backend — controller endpoint

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`

- [ ] **Step 1: Replace the `listPosts` method**

Open the file. Find the method:

```java
@GetMapping("/{slug}/posts")
public List<PostResponse> listPosts(@PathVariable String slug) {
    tenantService.findBySlug(slug);
    TenantContext.setCurrentTenant(slug);
    try {
        return postService.listAll();
    } finally {
        TenantContext.clear();
    }
}
```

Replace with:

```java
@GetMapping("/{slug}/posts")
public Page<PostResponse> listPosts(@PathVariable String slug, Pageable pageable) {
    tenantService.findBySlug(slug);
    TenantContext.setCurrentTenant(slug);
    try {
        return postService.listPublicPaged(pageable);
    } finally {
        TenantContext.clear();
    }
}
```

Add imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

Remove the `import java.util.List;` only if it's no longer used elsewhere in the file (grep the file for `List<`).

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile 2>&1 | tail -10`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run full backend suite**

Run: `./mvnw test 2>&1 | tail -15`

Expected: all tests pass (390+). If any test previously called `GET /api/salon/{slug}/posts` expecting a bare array, it may fail and need updating to parse `page.content[]`. Fix any such test inline.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java
git commit -m "feat(feed): paginate /api/salon/:slug/posts endpoint"
```

---

## Task 4: Frontend — posts service signature

**Files:**
- Modify: `frontend/src/app/features/posts/posts.service.ts`
- Create: `frontend/src/app/features/posts/posts.service.spec.ts`

- [ ] **Step 1: Check existing Page<T> type in the frontend**

Run: `grep -rn "export interface Page" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/shared/models 2>/dev/null`

There should be a shared `Page<T>` interface (used by bookings, notifications). Note its import path. If absent, the `notifications.service.ts` defines one inline — use the shared one if it exists.

- [ ] **Step 2: Update the `listPublic` method**

Current code in `posts.service.ts`:

```typescript
listPublic(slug: string): Observable<PostResponse[]> {
  return this.http.get<PostResponse[]>(`${this.apiBaseUrl}/api/salon/${slug}/posts`);
}
```

Replace with:

```typescript
listPublic(
  slug: string,
  page = 0,
  size = 20,
): Observable<Page<PostResponse>> {
  const params = new HttpParams()
    .set('page', page)
    .set('size', size);
  return this.http.get<Page<PostResponse>>(
    `${this.apiBaseUrl}/api/salon/${slug}/posts`,
    { params },
  );
}
```

Add imports at the top:

```typescript
import { HttpParams } from '@angular/common/http';
import { Page } from '../../shared/models/page.model'; // use the actual path discovered in Step 1
```

If no shared `Page<T>` exists, define one inline above the class:

```typescript
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
```

- [ ] **Step 3: Write a unit test**

Create `frontend/src/app/features/posts/posts.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PostsService } from './posts.service';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('PostsService', () => {
  let service: PostsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
      ],
    });
    service = TestBed.inject(PostsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listPublic sends page and size query params', () => {
    service.listPublic('my-salon', 2, 15).subscribe();
    const req = http.expectOne(r =>
      r.url === 'http://test/api/salon/my-salon/posts' &&
      r.params.get('page') === '2' &&
      r.params.get('size') === '15'
    );
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 15, first: true, last: true, empty: true });
  });

  it('listPublic defaults to page=0 size=20', () => {
    service.listPublic('my-salon').subscribe();
    const req = http.expectOne(r =>
      r.url === 'http://test/api/salon/my-salon/posts' &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20'
    );
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true, empty: true });
  });
});
```

- [ ] **Step 4: Run the test**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/posts.service.spec.ts' --watch=false 2>&1 | tail -15`

Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/posts/posts.service.ts \
        frontend/src/app/features/posts/posts.service.spec.ts
git commit -m "feat(feed): PostsService.listPublic accepts page and size"
```

---

## Task 5: Frontend — `SalonPostsViewerComponent` pagination + lazy images

**Files:**
- Modify: `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts`

- [ ] **Step 1: Replace pagination state and loader**

Find the existing `loadPosts(slug: string)` method (around line 652) and the `constructor()` `effect` that calls it. Read them.

Replace the `loadPosts` method and add pagination state. Full new section:

```typescript
// Signals (add to the existing signals list if they don't exist)
readonly posts = signal<PostResponse[]>([]);
readonly loading = signal(false);
readonly hasMore = signal(false);
readonly pageIndex = signal(0);

private readonly PAGE_SIZE = 20;

private loadInitial(slug: string): void {
  this.loading.set(true);
  this.pageIndex.set(0);
  this.posts.set([]);
  this.postsService.listPublic(slug, 0, this.PAGE_SIZE).subscribe({
    next: (page) => {
      this.posts.set(page.content);
      this.hasMore.set(!page.last);
      this.loading.set(false);
    },
    error: () => {
      this.posts.set([]);
      this.hasMore.set(false);
      this.loading.set(false);
    },
  });
}

private loadMore(): void {
  const slug = this.slug();
  if (!slug || this.loading() || !this.hasMore()) return;
  this.loading.set(true);
  const nextPage = this.pageIndex() + 1;
  this.postsService.listPublic(slug, nextPage, this.PAGE_SIZE).subscribe({
    next: (page) => {
      this.posts.set([...this.posts(), ...page.content]);
      this.pageIndex.set(nextPage);
      this.hasMore.set(!page.last);
      this.loading.set(false);
    },
    error: () => {
      this.loading.set(false);
    },
  });
}
```

Preserve the existing `constructor()` `effect` but have it call `loadInitial` instead of `loadPosts`:

```typescript
constructor() {
  effect(() => {
    const slugVal = this.slug();
    if (slugVal) {
      this.loadInitial(slugVal);
    }
  });
  const user = this.authService.user();
  this.isPro.set(user?.role === Role.PRO || user?.role === Role.ADMIN);
}
```

Delete the old `private loadPosts(slug: string)` method (the one using `listPublic(slug)` with no pagination).

- [ ] **Step 2: Trigger `loadMore` from the snap-scroll handler**

Find the existing `onScroll()` method (around line 666):

```typescript
onScroll(): void {
  const el = this.snapScroll()?.nativeElement;
  if (!el) return;
  const idx = Math.round(el.scrollTop / el.clientHeight);
  this.currentIndex.set(idx);
}
```

Modify to:

```typescript
onScroll(): void {
  const el = this.snapScroll()?.nativeElement;
  if (!el) return;
  const idx = Math.round(el.scrollTop / el.clientHeight);
  this.currentIndex.set(idx);
  // Trigger next-page when user is within 3 items of the end
  const total = this.posts().length;
  if (total > 0 && idx >= total - 3 && this.hasMore() && !this.loading()) {
    this.loadMore();
  }
}
```

- [ ] **Step 3: Add `loading="lazy"` to every `<img>` in the template**

In the inline template string, find each `<img ...>` tag and add `loading="lazy"`:

Examples (adapt to actual markup):
- `<img [src]="imgUrl(post.afterImageUrl)" class="ba-img ba-after" alt="After" draggable="false" />`
  → add ` loading="lazy"`
- `<img [src]="imgUrl(post.beforeImageUrl)" class="ba-img ba-before" ... />` → same
- Carousel/photo `<img>` tags → same

Search for `<img ` in the file and apply to every occurrence.

- [ ] **Step 4: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15`

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts
git commit -m "feat(feed): infinite scroll pagination and lazy images in salon posts viewer"
```

---

## Task 6: Frontend — `RecentPostsViewerComponent` lazy images

**Files:**
- Modify: `frontend/src/app/features/posts/recent-posts-viewer/recent-posts-viewer.component.ts`

- [ ] **Step 1: Add `loading="lazy"` to every `<img>`**

Open the file. Find each `<img ...>` in the inline template. Add `loading="lazy"` attribute. No other change.

- [ ] **Step 2: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -10`

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/posts/recent-posts-viewer/recent-posts-viewer.component.ts
git commit -m "feat(feed): lazy-load images in recent posts viewer"
```

---

## Task 7: Manual QA

**Files:** none — QA only.

- [ ] **Step 1: Start the dev server**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start -- --port 4200
```

Wait for `Local: http://localhost:4200/`.

- [ ] **Step 2: Seed or pick a salon with many posts**

Open a salon page that has at least 25 posts (so we can test the page boundary).

Navigate to `/salon/beaute-du-regard` (or any salon with posts).

- [ ] **Step 3: Verify initial pagination**

Open DevTools Network tab. Filter by XHR. You should see one request:
`GET /api/salon/:slug/posts?page=0&size=20` → response has 20 items + `last=false`.

- [ ] **Step 4: Verify infinite scroll**

Use the vertical snap-scroll (swipe down or scroll down with mouse). Around post #17–18, a new XHR should fire:
`GET /api/salon/:slug/posts?page=1&size=20` → response has remaining items + `last=true` (if total ≤ 40).

- [ ] **Step 5: Verify lazy image loading**

Reload the page. In DevTools Network tab, filter by `Img`. Confirm only images for the first 1–2 visible posts load initially. As you scroll, additional images appear in the network log.

- [ ] **Step 6: Verify desktop + mobile**

Switch viewport (`Cmd+Shift+M`) between desktop and iPhone SE. The feed should behave identically.

- [ ] **Step 7: No commit if all checks pass**

If a bug is found, fix in a new commit.

---

## Self-Review

**Spec coverage:**
- ✅ Backend paged repository → Task 1
- ✅ Backend paged service method → Task 2
- ✅ Backend paged controller endpoint → Task 3
- ✅ Frontend service signature with `page`/`size` → Task 4
- ✅ Frontend pagination state + infinite scroll → Task 5
- ✅ Lazy `loading="lazy"` on salon-posts images → Task 5
- ✅ Lazy `loading="lazy"` on recent-posts images → Task 6
- ✅ Manual QA → Task 7

**Placeholder check:** None.

**Type consistency:** `Page<T>`, `PostResponse`, `listPublicPaged` (backend), `listPublic` (frontend, kept name, extended signature), `loadInitial`, `loadMore`, `hasMore`, `pageIndex` — consistent across tasks.

**Shared `listAll` preserved:** `PostService.listAll()` remains untouched; only `listPublicPaged` is added. The pro `/api/pro/posts` endpoint is unaffected.

**Endpoint boundary:** `max-page-size=100` (set earlier in the security hardening) caps any `size=100000` abuse — no extra validation needed here.
