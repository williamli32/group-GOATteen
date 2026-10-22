import { Component, input, output } from '@angular/core';
import { boundedPage, pageCount } from '../core/utils/reporting-workflow';

@Component({
  selector: 'app-result-pagination',
  standalone: true,
  template: ` <nav [attr.aria-label]="label() + ' pages'">
    <span role="status">{{ start }}–{{ end }} of {{ total() }}</span>
    <label
      >Rows
      <select
        [value]="pageSize()"
        (change)="pageSizeChange.emit(+$any($event.target).value)"
        [disabled]="disabled()"
        [attr.aria-label]="label() + ' rows per page'"
      >
        <option value="10">10</option>
        <option value="25">25</option>
        <option value="50">50</option>
      </select></label
    >
    <button
      type="button"
      (click)="go(1)"
      [disabled]="disabled() || current === 1"
      aria-label="First page"
    >
      «
    </button>
    <button type="button" (click)="go(current - 1)" [disabled]="disabled() || current === 1">
      Previous
    </button>
    <label
      >Page
      <input
        type="number"
        [value]="current"
        min="1"
        [max]="pages"
        (change)="jump($event)"
        [disabled]="disabled()"
        [attr.aria-label]="label() + ' page number'"
      />
      of {{ pages }}</label
    >
    <button type="button" (click)="go(current + 1)" [disabled]="disabled() || current === pages">
      Next
    </button>
    <button
      type="button"
      (click)="go(pages)"
      [disabled]="disabled() || current === pages"
      aria-label="Last page"
    >
      »
    </button>
  </nav>`,
  styles: [
    `
      :host {
        display: block;
      }
      nav {
        display: flex;
        gap: 8px;
        align-items: center;
        flex-wrap: wrap;
        padding: 12px 0;
        color: var(--muted);
        font-size: 12px;
      }
      label {
        display: flex;
        gap: 6px;
        align-items: center;
      }
      input,
      select,
      button {
        font: inherit;
        color: inherit;
        background: var(--surface);
        border: 1px solid var(--line-strong);
        border-radius: 8px;
        min-height: 40px;
        padding: 5px 8px;
      }
      input {
        width: 66px;
      }
      button {
        cursor: pointer;
      }
      button:not(:disabled):hover {
        background: var(--emerald-soft);
        color: var(--emerald);
      }
      button:disabled {
        opacity: 0.45;
        cursor: default;
      }
      button:focus-visible,
      input:focus-visible,
      select:focus-visible {
        outline: 2px solid var(--emerald);
        outline-offset: 2px;
      }
    `,
  ],
})
export class ResultPaginationComponent {
  readonly label = input('Results');
  readonly total = input(0);
  readonly page = input(1);
  readonly pageSize = input(10);
  readonly disabled = input(false);
  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();
  get pages(): number {
    return pageCount(this.total(), this.pageSize());
  }
  get current(): number {
    return boundedPage(this.page(), this.total(), this.pageSize());
  }
  get start(): number {
    return this.total() ? (this.current - 1) * this.pageSize() + 1 : 0;
  }
  get end(): number {
    return Math.min(this.current * this.pageSize(), this.total());
  }
  jump(event: Event): void {
    const control = event.target as HTMLInputElement;
    const page = Number.isFinite(control.valueAsNumber)
      ? boundedPage(control.valueAsNumber, this.total(), this.pageSize())
      : this.current;
    control.value = String(page);
    this.go(page);
  }
  go(page: number): void {
    if (Number.isFinite(page))
      this.pageChange.emit(boundedPage(page, this.total(), this.pageSize()));
  }
}
