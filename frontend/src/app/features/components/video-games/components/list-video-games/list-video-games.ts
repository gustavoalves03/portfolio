import {Component, inject, signal} from '@angular/core';
import {GetAllVideoGames} from '../../services/get-all-video-games';
import {AsyncPipe} from '@angular/common';
import {TableVideoGames} from '../table-video-games/table-video-games';
import {VideoGame} from '../../models/video-games';

@Component({
  selector: 'app-list-video-games',
  imports: [AsyncPipe, TableVideoGames, TableVideoGames],
  templateUrl: './list-video-games.html',
  styleUrl: './list-video-games.scss',
})
export class ListVideoGames {

  private readonly service = inject(GetAllVideoGames);
  videoGamesResult$ = this.service.getAll();

  title = signal('List Video Games');

  changeTitle() {
    this.title.set('Les jeux-vidéos (OK)');
  }

  editOne(item: VideoGame) {
    console.log('editOne', item);
  }
}
