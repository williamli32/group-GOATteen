import { DOCUMENT } from '@angular/common';
import { AfterViewInit, Directive, ElementRef, OnDestroy, inject, output } from '@angular/core';

/** Focus, inert background, Escape and focus restoration for the app's custom modal. */
@Directive({
  selector: '[appModalFocus]',
  standalone: true,
  host: {
    tabindex: '-1',
    '(keydown)': 'onKeydown($event)',
    '(document:focusin)': 'onFocus($event)',
  },
})
export class ModalFocusDirective implements AfterViewInit, OnDestroy {
  readonly modalEscape = output<void>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly document = inject(DOCUMENT);
  private previousFocus: HTMLElement | null = null;
  private previousOverflow = '';
  private inert: HTMLElement[] = [];
  private active = false;

  ngAfterViewInit(): void {
    this.previousFocus = this.document.activeElement as HTMLElement | null;
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    let branch: HTMLElement | null = this.host;
    while (branch && branch !== this.document.body) {
      for (const sibling of Array.from(branch.parentElement?.children ?? [])) {
        if (
          sibling instanceof HTMLElement &&
          sibling !== branch &&
          !sibling.hasAttribute('inert')
        ) {
          sibling.setAttribute('inert', '');
          this.inert.push(sibling);
        }
      }
      branch = branch.parentElement;
    }
    this.active = true;
    this.focusFirst();
  }

  private controls(): HTMLElement[] {
    return Array.from(
      this.host.querySelectorAll<HTMLElement>(
        'button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]',
      ),
    ).filter((element) => !element.closest('[hidden],[inert]'));
  }

  private focusFirst(): void {
    (this.controls()[0] ?? this.host).focus();
  }

  onFocus(event: FocusEvent): void {
    if (this.active && !this.host.contains(event.target as Node | null)) this.focusFirst();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      this.modalEscape.emit();
    } else if (event.key === 'Tab') {
      const controls = this.controls();
      const first = controls[0] ?? this.host;
      const last = controls[controls.length - 1] ?? this.host;
      if (
        event.shiftKey &&
        (this.document.activeElement === first || this.document.activeElement === this.host)
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (this.document.activeElement === last || controls.length === 0)
      ) {
        event.preventDefault();
        first.focus();
      }
    }
  }

  ngOnDestroy(): void {
    this.active = false;
    this.inert.forEach((element) => element.removeAttribute('inert'));
    this.document.body.style.overflow = this.previousOverflow;
    if (this.previousFocus?.isConnected && !this.previousFocus.closest('[hidden],[inert]')) {
      const fallback = this.previousFocus
        .closest('form')
        ?.querySelector<HTMLElement>('input:not(:disabled),button:not(:disabled)');
      (this.previousFocus.matches(':disabled') ? fallback : this.previousFocus)?.focus();
    }
  }
}
