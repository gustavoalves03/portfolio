import {Component, inject, signal} from '@angular/core';
import {GetAllVideoGamesService} from '../../data/services/get-all-video-games.service';
import {AsyncPipe} from '@angular/common';
import {TableVideoGames} from '../table-video-games/table-video-games';
import {VideoGame} from '../../data/models/video-games';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-list-video-games',
  imports: [AsyncPipe, TableVideoGames, TableVideoGames, FormsModule],
  templateUrl: './list-video-games.html',
  styleUrl: './list-video-games.scss',
})
export class ListVideoGames {

  private readonly service = inject(GetAllVideoGamesService);
  videoGamesResult$ = this.service.getAll();

  title = signal('List Video Games');

  changeTitle() {
    this.title.set('Les jeux-vidéos (OK)');
  }

  videoGameToBeEdited = signal<VideoGame | null | undefined>(undefined);

  editOne(item: VideoGame) {
    this.videoGameToBeEdited.set(item);
  }

  saveOne(item: VideoGame) {
    console.log('saveOne', item);
  }
}

