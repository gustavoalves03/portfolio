# Feed Pagination + Image Lazy-Loading — Design Spec

**Date:** 2026-04-19
**Status:** Approved (pending user review)
**Scope:** Backend pagination on `GET /api/salon/:slug/posts` + native lazy-loading on post images across the feed.

## Goal

Prevent unbounded loading of salon posts (currently returns all posts for a salon in a single response) and stop the browser from eagerly fetching every image when a page with many posts is rendered.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Scope | `/api/salon/:slug/posts` only (pro-side and public recent stay untouched) |
| Lazy-loading strategy | Native HTML `loading="lazy"` |
| Page size | 20 posts |
| Public `/api/public/posts/recent` | Keep as-is (curated 6×6 feed — not meant to be infinite) |

## Architecture

### Backend — one endpoint paginated

**File:** `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`

Change the `listPosts` handler to accept a `Pageable`:

```java
@GetMapping("/{slug}/posts")
public Page<PostResponse> listPosts(
    @PathVariable String slug,
    Pageable pageable) {
  return applicationSchemaExecutor.callForTenant(slug, () ->
      postService.listPublic(pageable));
}
```

**File:** `PostService.java` — new method:

```java
public Page<PostResponse> listPublic(Pageable pageable) {
  return postRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
}
```

**File:** `PostRepository.java` — add method:

```java
Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
```

**No breaking change**. The existing `spring.data.web.pageable.max-page-size=100` (set during the security hardening) clamps `size=100000` abuse.

### Frontend — service + component

**File:** `frontend/src/app/features/posts/posts.service.ts`

Change `getSalonPosts` to accept pagination params and return a `Page<PostResponse>`:

```typescript
getSalonPosts(
  slug: string,
  page = 0,
  size = 20,
): Observable<Page<PostResponse>> {
  let params = new HttpParams()
    .set('page', page)
    .set('size', size);
  return this.http.get<Page<PostResponse>>(
    `${this.apiBaseUrl}/api/salon/${slug}/posts`,
    { params },
  );
}
```

**File:** `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts`

Local state (signals):
- `posts: signal<PostResponse[]>([])`
- `page: signal<number>(0)`
- `hasMore: signal<boolean>(false)`
- `loading: signal<boolean>(false)`

Methods:
- `loadInitial()` — called on init via `constructor` / `ngOnInit`. Resets state, calls `getSalonPosts(slug, 0, 20)`, stores `res.content`, sets `hasMore = !res.last`.
- `loadNextPage()` — guards on `loading` + `hasMore`; calls `getSalonPosts(slug, page + 1, 20)` and concatenates items.

Infinite scroll: add a `#sentinel` div at the bottom of the post list. `IntersectionObserver` setup in `afterNextRender` + `ngAfterViewInit`, same pattern as `pro-booking-history.component.ts` and `notifications.component.ts`.

### Frontend — lazy-load images

Add `loading="lazy"` to every `<img>` in:
- `salon-posts-viewer.component.ts` (main post images, carousel thumbnails, before/after pairs)
- `recent-posts-viewer.component.ts` (home feed)

Example:
```html
<img [src]="imgUrl(post.imagePath)" loading="lazy" alt="" />
```

No JS, no directive. Browser handles the viewport threshold. Works in all modern browsers (~96% coverage).

## Testing

### Backend (JUnit)

- `PostServiceTests` (new or existing): `listPublic_returnsPagedPosts_sortedByCreatedAtDesc`
- `PublicSalonControllerTests` (new or extend): `listPosts_returnsPagedResponse_withSizeAndPage`

Additionally verify that the `max-page-size=100` clamp applies (pattern already established in `NotificationControllerTests`).

### Frontend (Jasmine/Karma)

- `PostsService` spec: `getSalonPosts` sends correct `page` + `size` params and returns `Page<PostResponse>`
- `SalonPostsViewerComponent`:
  - Renders skeleton while loading initial
  - Renders posts after initial load
  - `loadNextPage` appends items when `hasMore=true`
  - No-op when `hasMore=false`

No frontend unit test specifically for `loading="lazy"` — it's a plain HTML attribute.

### Manual QA

1. Populate a salon with 50 posts in DB
2. Open `/salon/:slug` — verify only the first 20 load initially
3. Scroll to bottom — verify spinner + next batch loads
4. Reach end — verify no more fetches + "end of feed" (or no more messages)
5. DevTools Network → verify images load on viewport entry (not all at once)

## Out of scope

- Pagination of `/api/pro/posts` (pro-side)
- Pagination of `/api/public/posts/recent` (curated feed, bounded by design)
- Image resizing / thumbnail generation
- CDN / S3 migration
- Placeholder blur-up (low-res preview)
- Custom `IntersectionObserver` directive — native `loading="lazy"` is sufficient

## Rollout

Single PR. Order of implementation:

1. Backend repo + service + controller + tests
2. Frontend `posts.service.ts` signature
3. Frontend `SalonPostsViewerComponent` refactor (infinite scroll state + sentinel)
4. Frontend `loading="lazy"` on post images (2 components)
5. Build + smoke test
