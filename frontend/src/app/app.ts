import { Component, OnInit, inject, effect } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { LoadingSpinner } from './shared/components/loading-spinner/loading-spinner';
import { Sidebar } from './shared/components/sidebar/sidebar';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LoadingSpinner, Sidebar],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  readonly auth = inject(AuthService);
  sidebarExpanded = false;

  constructor() {
    effect(() => {
      const user = this.auth.currentUser();
      if (user) {
        console.log('[Auth] Session active →', user.username, '| Rôles:', user.roles);
      } else if (!this.auth.isLoading()) {
        console.log('[Auth] Aucune session active');
      }
    });
  }

  ngOnInit(): void {
    this.auth.checkSession().subscribe();
  }

  onSidebarExpandedChange(expanded: boolean): void {
    this.sidebarExpanded = expanded;
  }
}