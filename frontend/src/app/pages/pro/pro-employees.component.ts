import { Component } from '@angular/core';

@Component({
    selector: 'app-pro-employees',
    standalone: true,
    template: `<div class="page"><h1>Mon Équipe</h1></div>`,
    styles: [`.page { max-width: 800px; margin: 0 auto; padding: 1.5rem; } h1 { font-size: 20px; font-weight: 600; color: #333; }`],
})
export class ProEmployeesComponent {}
