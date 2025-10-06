import {Component, input, output} from '@angular/core';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';

/**
 * @title Binding event handlers and properties to the table rows.
 */
@Component({
  selector: 'crud-table',
  standalone: true,
  styleUrl: 'crud-table.scss',
  templateUrl: 'crud-table.html',
  imports: [MatTableModule, MatButtonModule, MatIconModule],
})
export class CrudTable {

  displayedColumns = input.required<string[]>();
  dataSource = input.required<any[]>();
  emptyMessage = input<string>('Aucun élément à afficher');
  title = input<string>('');
  showSearch = input<boolean>(true);
  searchPlaceholder = input<string>('Recherche...');
  searchMaxWidth = input<string>('200px');
  addItem = output<void>();
  searchChange = output<string>();

  onSearch(value: string) {
    this.searchChange.emit(value);
  }

}
