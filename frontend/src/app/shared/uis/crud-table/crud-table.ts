import { Component, input, output, computed, effect } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { TableColumn, TableAction } from './crud-table.models';

/**
 * @title Binding event handlers and properties to the table rows.
 */
@Component({
  selector: 'crud-table',
  standalone: true,
  styleUrl: 'crud-table.scss',
  templateUrl: 'crud-table.html',
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule, TranslocoPipe],
})
export class CrudTable {

  // Nouvelles entrées avec configuration complète
  columns = input<TableColumn[]>([]);
  actions = input<TableAction[]>([]);

  // Anciennes entrées (deprecated mais maintenues pour compatibilité)
  displayedColumns = input<string[]>([]);

  dataSource = input.required<any[]>();
  emptyMessage = input<string>('Aucun élément à afficher');
  title = input<string>('');
  showSearch = input<boolean>(true);
  searchPlaceholder = input<string>('Recherche...');
  searchMaxWidth = input<string>('200px');
  addItem = output<void>();
  searchChange = output<string>();

  // Computed: génère les noms de colonnes à partir de la configuration
  protected readonly columnKeys = computed(() => {
    const cols = this.columns();
    const displayedCols = this.displayedColumns();

    // Nouveau système avec columns
    if (cols && cols.length > 0) {
      const keys = cols.map(c => c.key);
      // Ajouter 'actions' si des actions sont définies
      if (this.actions().length > 0) {
        keys.push('actions');
      }
      return keys;
    }

    // Fallback sur l'ancien système avec displayedColumns
    if (displayedCols && displayedCols.length > 0) {
      return displayedCols;
    }

    // Par défaut, retourner un tableau vide
    return [];
  });


  // Méthode pour obtenir la config d'une colonne par sa clé
  protected getColumnConfig(key: string): TableColumn | undefined {
    return this.columns().find(c => c.key === key);
  }

  protected resolveCellValue(columnKey: string, row: any): string | number | null | undefined {
    const column = this.getColumnConfig(columnKey);
    if (!column) {
      return row?.[columnKey];
    }
    if (column.valueGetter) {
      return column.valueGetter(row);
    }
    return row?.[columnKey];
  }

  onSearch(value: string) {
    this.searchChange.emit(value);
  }

}
