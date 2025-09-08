import {Component, OnInit} from '@angular/core';


@Component({
  selector: 'task',
  templateUrl: './task.component.html',
  styleUrl: './task.component.scss'
})
export class TaskComponent implements OnInit {

  constructor() {
  }

  ngOnInit(): void {
    console.log('TaskComponent');
  }
}
