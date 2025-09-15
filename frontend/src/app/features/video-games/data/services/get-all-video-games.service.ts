import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {VideoGameResponse} from '../models/video-games';

@Injectable({
  providedIn: 'root'
})
export class GetAllVideoGamesService {

  private readonly httpClient = inject(HttpClient)
  private readonly url = 'https://jsonfakery.com/games/paginated'

  getAll(): Observable<VideoGameResponse> {
    return this.httpClient.get<VideoGameResponse>(this.url)
  }

}

