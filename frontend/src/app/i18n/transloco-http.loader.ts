import {inject, Injectable, PLATFORM_ID} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';

@Injectable({providedIn: 'root'})
export class TranslocoHttpLoader {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  getTranslation(lang: string): Observable<Record<string, any>> {
    // In SSR context, return embedded translations to avoid HTTP calls
    if (!isPlatformBrowser(this.platformId)) {
      return of(this.getEmbeddedTranslation(lang));
    }

    // In browser, load from public assets
    const url = `/i18n/${lang}.json`;
    return this.http.get<Record<string, any>>(url);
  }

  private getEmbeddedTranslation(lang: string): Record<string, any> {
    // Embedded translations for SSR - synchronized with public/i18n/*.json
    const translations: Record<string, Record<string, any>> = {
      fr: {
        app: { title: 'Fleur de Coquillage' },
        table: {
          search: { placeholder: 'Recherche...' },
          empty: 'Aucun élément à afficher'
        },
        actions: {
          edit: 'Modifier',
          delete: 'Supprimer'
        },
        cares: {
          title: 'Prestations',
          add: 'Ajouter',
          columns: {
            name: 'Nom',
            description: 'Description',
            price: 'Prix',
            duration: 'Durée',
            status: 'Statut'
          },
          status: {
            ACTIVE: 'Actif',
            INACTIVE: 'Inactif'
          }
        },
        categories: {
          title: 'Catégories',
          columns: {
            name: 'Nom',
            description: 'Description'
          }
        },
        bookings: {
          title: 'Réservations',
          columns: {
            id: 'ID',
            userId: 'Utilisateur',
            careId: 'Prestation',
            quantity: 'Quantité',
            status: 'Statut',
            createdAt: 'Date de création'
          }
        },
        users: {
          title: 'Utilisateurs',
          columns: {
            id: 'ID',
            name: 'Nom',
            email: 'Email'
          }
        }
      },
      en: {
        app: { title: 'Fleur de Coquillage' },
        table: {
          search: { placeholder: 'Search...' },
          empty: 'No items to display'
        },
        actions: {
          edit: 'Edit',
          delete: 'Delete'
        },
        cares: {
          title: 'Cares',
          add: 'Add',
          columns: {
            name: 'Name',
            description: 'Description',
            price: 'Price',
            duration: 'Duration',
            status: 'Status'
          },
          status: {
            ACTIVE: 'Active',
            INACTIVE: 'Inactive'
          }
        },
        categories: {
          title: 'Categories',
          columns: {
            name: 'Name',
            description: 'Description'
          }
        },
        bookings: {
          title: 'Bookings',
          columns: {
            id: 'ID',
            userId: 'User',
            careId: 'Care',
            quantity: 'Quantity',
            status: 'Status',
            createdAt: 'Created At'
          }
        },
        users: {
          title: 'Users',
          columns: {
            id: 'ID',
            name: 'Name',
            email: 'Email'
          }
        }
      }
    };

    return translations[lang] || translations['fr'];
  }
}
