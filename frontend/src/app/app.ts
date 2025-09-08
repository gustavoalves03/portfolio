import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {MainMenu} from './shared/uis/manus/main-menu/main-menu';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MainMenu],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('app');
}
