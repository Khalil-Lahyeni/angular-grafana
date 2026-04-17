import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';

type SidebarItem = {
  label: string;
  route: string;
  icon: string;
};

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly auth = inject(AuthService);

  @Output() readonly expandedChange = new EventEmitter<boolean>();

  readonly expanded = signal(false);
  readonly user = this.auth.currentUser;
  readonly avatarLetter = computed(() => this.user()?.username?.charAt(0).toUpperCase() ?? 'U');

  readonly items: SidebarItem[] = [
    { label: 'Dashboard', route: '/dashboard', icon: 'grid' },
    { label: 'Alerts', route: '/alerts', icon: 'bell' },
    { label: 'Settings', route: '/settings', icon: 'gear' },
  ];

  onMouseEnter(): void {
    this.expanded.set(true);
    this.expandedChange.emit(true);
  }

  onMouseLeave(): void {
    this.expanded.set(false);
    this.expandedChange.emit(false);
  }

  logout(): void {
    this.auth.logout();
  }
}
