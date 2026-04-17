import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withViewTransitions,
  withPreloading,
  PreloadAllModules,
} from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),

    provideRouter(
      routes,
      withComponentInputBinding(),
      // Précharge tous les lazy components en arrière-plan → élimine le flash
      withPreloading(PreloadAllModules),
      // View Transitions API : fondu natif entre routes
      withViewTransitions(),
    ),

    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
  ],
};