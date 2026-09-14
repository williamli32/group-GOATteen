import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
<<<<<<< HEAD
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('frontend');
=======
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('trading-platform-ui');
>>>>>>> 272756fdd31cbc5e77a8f9646662bbac0fccf6b4
}
