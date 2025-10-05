import {Component, input} from '@angular/core';
import {MatTableModule} from '@angular/material/table';
import {VideoGame} from '../../../features/video-games/data/models/video-games';

/**
 * @title Binding event handlers and properties to the table rows.
 */
@Component({
  selector: 'crud-table',
  styleUrl: 'crud-table.scss',
  templateUrl: 'crud-table.html',
  imports: [MatTableModule],
})
export class CrudTable {

  displayedColumns = input.required<string[]>();
  dataSource = input.required<any[]>();

}
