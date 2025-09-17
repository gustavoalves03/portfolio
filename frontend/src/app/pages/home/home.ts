import { Component } from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';

@Component({
  selector: 'app-home',
  imports: [MatCardModule, CurrencyPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss'
})
export class Home {
  services = [
    { id: 1, name: 'Soin du visage', description: 'Nettoyage, masque et hydratation', price: 45, duration: 45 },
    { id: 2, name: 'Massage relaxant', description: 'Détente corps entier', price: 60, duration: 60 },
    { id: 3, name: 'Manucure', description: 'Soin des mains et vernis', price: 30, duration: 30 },
  ];
}
