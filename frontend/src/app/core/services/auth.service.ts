import { Injectable, inject, signal, computed, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of, map } from 'rxjs';

export interface UserInfo {
  username: string;
  email: string;
  roles: string[];
}

const GATEWAY_URL = 'http://localhost:8888';
const FRONTEND_URL = 'http://localhost:4200';
const BROADCAST_CHANNEL_NAME = 'auth_channel';

@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly http = inject(HttpClient);

  // ── Signals ──────────────────────────────────────────
  currentUser = signal<UserInfo | null>(null);
  isAuthenticated = computed(() => this.currentUser() !== null);
  isLoading = signal<boolean>(true);

  // ── BroadcastChannel : communication inter-onglets ───
  private readonly channel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);

  constructor() {
    // Écoute les messages des autres onglets
    this.channel.onmessage = (event) => {
      if (event.data === 'LOGOUT') {
        // Un autre onglet a déclenché le logout → on redirige aussi
        this.currentUser.set(null);
        window.location.href = `${GATEWAY_URL}/logout`;
      }
    };
  }

  // ── Session check ─────────────────────────────────────
  checkSession(): Observable<void> {
    return this.http.get<UserInfo>(`${GATEWAY_URL}/api/user`).pipe(
      tap((user) => {
        this.currentUser.set(user);
        this.isLoading.set(false);
      }),
      catchError(() => {
        this.currentUser.set(null);
        this.isLoading.set(false);
        return of(null);
      }),
      map(() => void 0)
    );
  }

  // ── Login ─────────────────────────────────────────────
  login(returnUrl: string = '/'): void {
    const redirectUri = encodeURIComponent(`${FRONTEND_URL}${returnUrl}`);
    window.location.href =
      `${GATEWAY_URL}/oauth2/authorization/keycloak?redirect_uri=${redirectUri}`;
  }

  // ── Logout : notifie tous les onglets puis redirige ───
  logout(): void {
    // Broadcast aux autres onglets AVANT de rediriger
    this.channel.postMessage('LOGOUT');
    // Redirige l'onglet courant vers le Gateway logout
    window.location.href = `${GATEWAY_URL}/logout`;
  }

  ngOnDestroy(): void {
    this.channel.close();
  }
}