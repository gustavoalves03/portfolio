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
