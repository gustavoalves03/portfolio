


export interface VideoGame {

  id: string;
  name: string;
  released: string;
  rating: number;
  genres: string[];
  background_image: string;

}

export type VideoGameResponse = {
  data: VideoGame[];
}
