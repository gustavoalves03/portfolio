import {Component, input, output} from '@angular/core';
import {VideoGame} from '../../models/video-games';

@Component({
  selector: 'app-table-video-games',
  imports: [],
  templateUrl: './table-video-games.html',
  styleUrl: './table-video-games.scss'
})
export class TableVideoGames {
  items = input.required<VideoGame[]>();

  toEdit = output<VideoGame>();
}
