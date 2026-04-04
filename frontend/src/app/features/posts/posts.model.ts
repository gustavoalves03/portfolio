export type PostType = 'BEFORE_AFTER' | 'PHOTO' | 'CAROUSEL';

export interface PostResponse {
  id: number;
  type: PostType;
  caption: string | null;
  beforeImageUrl: string | null;
  afterImageUrl: string | null;
  carouselImageUrls: string[];
  careId: number | null;
  careName: string | null;
  createdAt: string;
}

export interface RecentPost {
  id: number;
  type: PostType;
  caption: string | null;
  thumbnailUrl: string | null;
  salonName: string | null;
  salonSlug: string | null;
  createdAt: string;
}
