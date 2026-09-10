import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [FormsModule],
  styleUrls: ['./auth.component.scss'],
  template: `
    <div class="min-h-full flex">
      <!-- Left panel -->
      <div class="hidden lg:flex flex-col justify-between w-1/2 bg-[#0d1225] border-r border-[#1e2d45] p-12 relative overflow-hidden">
        <div class="absolute inset-0 pointer-events-none">
          @for (i of grid8; track i) {
            <div class="absolute top-0 bottom-0 border-r border-[#1e2d4520]" [style.left]="((i + 1) * 12.5) + '%'"></div>
          }
          @for (i of grid8; track i) {
            <div class="absolute left-0 right-0 border-b border-[#1e2d4520]" [style.top]="((i + 1) * 12.5) + '%'"></div>
          }
        </div>
        <div class="relative">
          <span class="font-mono text-[#00d4aa] font-semibold tracking-widest text-lg">APEX</span>
          <span class="font-mono text-[#64748b] text-lg tracking-widest ml-1">TRADE</span>
        </div>
        <div class="relative space-y-6">
          <div class="inline-flex items-center gap-2 px-3 py-1.5 rounded border border-[#00d4aa30] bg-[#00d4aa0a]">
            <div class="w-1.5 h-1.5 rounded-full bg-[#00d4aa] animate-pulse"></div>
            <span class="font-mono text-xs text-[#00d4aa] tracking-wide">MARKETS OPEN</span>
          </div>
          <h1 class="text-4xl font-semibold text-[#e2e8f0] leading-tight">
            Institutional-grade<br>
            <span class="text-[#00d4aa]">execution</span> at<br>
            your fingertips.
          </h1>
          <p class="text-[#64748b] text-sm leading-relaxed max-w-xs">
            Real-time market data, sub-millisecond order routing, and full audit trails — built for professionals.
          </p>
          <div class="grid grid-cols-3 gap-4 pt-4">
            @for (stat of stats; track stat.label) {
              <div class="border border-[#1e2d45] rounded p-3">
                <p class="font-mono text-lg font-semibold text-[#00d4aa]">{{ stat.val }}</p>
                <p class="text-[10px] text-[#64748b] mt-0.5">{{ stat.label }}</p>
              </div>
            }
          </div>
        </div>
        <p class="relative text-[10px] text-[#64748b]">© 2026 ApexTrade Securities LLC. All rights reserved.</p>
      </div>

      <!-- Right panel -->
      <div class="flex-1 flex items-center justify-center p-8 bg-[#080c18]">
        <div class="w-full max-w-sm">
          <div class="mb-8">
            <div class="lg:hidden mb-6">
              <span class="font-mono text-[#00d4aa] font-semibold tracking-widest text-lg">APEX</span>
              <span class="font-mono text-[#64748b] text-lg tracking-widest ml-1">TRADE</span>
            </div>
            <h2 class="text-2xl font-semibold text-[#e2e8f0]">
              {{ mode === 'signin' ? 'Welcome back' : 'Create account' }}
            </h2>
            <p class="text-sm text-[#64748b] mt-1">
              {{ mode === 'signin' ? 'Sign in to your trading terminal' : 'Get started with ApexTrade' }}
            </p>
          </div>

          <!-- Tab toggle -->
          <div class="tab-toggle-container flex rounded border border-[#1e2d45] p-0.5 mb-6 bg-[#0d1225]">
            <button (click)="setMode('signin')" [class]="tabClass('signin')">Sign In</button>
            <button (click)="setMode('signup')" [class]="tabClass('signup')">Sign Up</button>
          </div>

          <form (ngSubmit)="handle()" #authForm="ngForm" class="space-y-4">
            @if (mode === 'signup') {
              <div>
                <label class="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Full Name</label>
                <input type="text" [(ngModel)]="name" name="name" placeholder="Alex Hartmann"
                  class="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors">
              </div>
            }
            <div>
              <label class="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Email</label>
              <input type="email" [(ngModel)]="email" name="email" placeholder="you@firm.com"
                class="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors">
            </div>
            <div>
              <label class="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Password</label>
              <input type="password" [(ngModel)]="password" name="password" placeholder="••••••••"
                class="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors">
            </div>
            @if (mode === 'signup') {
              <div>
                <label class="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Confirm Password</label>
                <input type="password" [(ngModel)]="confirm" name="confirm" placeholder="••••••••"
                  class="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors">
              </div>
            }
            @if (error) {
              <p class="text-xs text-[#ef4444] bg-[#ef444418] px-3 py-2 rounded border border-[#ef444430]">{{ error }}</p>
            }
            <button type="submit"
              class="w-full bg-[#00d4aa] hover:bg-[#00bfa0] text-[#080c18] font-semibold py-2.5 rounded text-sm transition-colors mt-2">
              {{ mode === 'signin' ? 'Access Terminal' : 'Create Account' }}
            </button>
          </form>

          @if (mode === 'signin') {
            <div class="mt-4 text-center">
              <a href="#" class="text-xs text-[#64748b] hover:text-[#94a3b8] transition-colors">Forgot password?</a>
            </div>
          }

          <p class="mt-6 text-center text-[10px] text-[#2a3f5f]">
            Protected by 256-bit TLS encryption · FINRA / SEC compliant
          </p>
        </div>
      </div>
    </div>
  `,
})
export class AuthComponent {
  mode: 'signin' | 'signup' = 'signin';
  name = '';
  email = '';
  password = '';
  confirm = '';
  error = '';

  grid8 = [0, 1, 2, 3, 4, 5, 6, 7];
  stats = [
    { label: 'Avg Fill Time', val: '4.2ms' },
    { label: 'Uptime SLA', val: '99.99%' },
    { label: 'Markets', val: '140+' },
  ];

  constructor(private router: Router) {}

  setMode(m: 'signin' | 'signup') {
    this.mode = m;
    this.error = '';
  }

  tabClass(m: 'signin' | 'signup'): string {
    if (this.mode === m) {
      return 'tab-button tab-button--active';
    }
    return 'tab-button tab-button--inactive';
  }

  handle() {
    if (this.mode === 'signup' && this.password !== this.confirm) {
      this.error = 'Passwords do not match.';
      return;
    }
    if (!this.email || !this.password) {
      this.error = 'Please fill in all required fields.';
      return;
    }
    this.router.navigate(['/dashboard']);
  }
}
