export interface TableColumn {
  /** Clé de la propriété dans l'objet de données */
  key: string;
  /** Clé de traduction pour le header (ex: 'cares.columns.name') */
  headerKey: string;
  /** Type de colonne pour gérer l'affichage */
  type?: 'text' | 'number' | 'date' | 'currency' | 'boolean' | 'actions';
  /** Largeur de la colonne (optionnel) */
  width?: string;
  /** Alignement du contenu */
  align?: 'left' | 'center' | 'right';
  /** Fonction optionnelle pour formater une valeur à l'affichage */
  valueGetter?: (row: any) => string | number | null | undefined;
}

export interface TableAction {
  /** Icône Material (ex: 'edit', 'delete') */
  icon: string;
  /** Clé de traduction pour le tooltip */
  tooltipKey: string;
  /** Couleur de l'icône (primary, accent, warn) */
  color?: 'primary' | 'accent' | 'warn';
  /** Callback appelé avec l'élément de la ligne */
  callback: (item: any) => void;
}
