import {
  Directive,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  HostListener,
  NgZone
} from '@angular/core';

export interface ResizeEvent {
  edges: {
    left?: number;
    right?: number;
    top?: number;
    bottom?: number;
  };
}

@Directive({
  selector: '[appResizable]',
  standalone: true
})
export class AppResizableDirective {

  @Input() resizeEdges: {
    left?: boolean;
    right?: boolean;
    top?: boolean;
    bottom?: boolean;
  } = {};

  @Input() minWidth?: number;
  @Input() maxWidth?: number;
  @Input() minHeight?: number;
  @Input() maxHeight?: number;

  @Output() resizeEnd = new EventEmitter<ResizeEvent>();

  private isResizing = false;
  private startX = 0;
  private startY = 0;
  private startWidth = 0;
  private startHeight = 0;
  private resizeType:
    'left' | 'right' | 'top' | 'bottom' | null = null;
  private readonly resizeZone = 10;

  constructor(
    private el: ElementRef,
    private ngZone: NgZone
  ) {
    const elem = this.el.nativeElement as HTMLElement;
    elem.style.position = 'relative';
  }

  @HostListener('mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    const elem = this.el.nativeElement as HTMLElement;
    const rect = elem.getBoundingClientRect();

    if (!this.isResizing) {
      let cursor = 'default';

      // LEFT
      if (this.resizeEdges.left) {
        const distanceFromLeft =
          event.clientX - rect.left;

        if (distanceFromLeft <= this.resizeZone) {
          cursor = 'col-resize';
        }
      }

      // RIGHT
      if (
        cursor === 'default' &&
        this.resizeEdges.right
      ) {
        const distanceFromRight =
          rect.right - event.clientX;

        if (distanceFromRight <= this.resizeZone) {
          cursor = 'col-resize';
        }
      }

      // TOP
      if (
        cursor === 'default' &&
        this.resizeEdges.top
      ) {
        const distanceFromTop =
          event.clientY - rect.top;

        if (distanceFromTop <= this.resizeZone) {
          cursor = 'row-resize';
        }
      }

      // BOTTOM
      if (
        cursor === 'default' &&
        this.resizeEdges.bottom
      ) {
        const distanceFromBottom =
          rect.bottom - event.clientY;

        if (distanceFromBottom <= this.resizeZone) {
          cursor = 'row-resize';
        }
      }

      elem.style.cursor = cursor;
    }
  }

  @HostListener('mousedown', ['$event'])
  onMouseDown(event: MouseEvent): void {
    const elem = this.el.nativeElement as HTMLElement;
    const rect = elem.getBoundingClientRect();

    let detectType:
      'left' | 'right' | 'top' | 'bottom' | null = null;

    // LEFT EDGE
    if (this.resizeEdges.left) {
      const distanceFromLeft =
        event.clientX - rect.left;

      if (distanceFromLeft <= this.resizeZone) {
        detectType = 'left';
      }
    }

    // RIGHT EDGE
    if (
      !detectType &&
      this.resizeEdges.right
    ) {
      const distanceFromRight =
        rect.right - event.clientX;

      if (distanceFromRight <= this.resizeZone) {
        detectType = 'right';
      }
    }

    // TOP EDGE
    if (
      !detectType &&
      this.resizeEdges.top
    ) {
      const distanceFromTop =
        event.clientY - rect.top;

      if (distanceFromTop <= this.resizeZone) {
        detectType = 'top';
      }
    }

    // BOTTOM EDGE
    if (
      !detectType &&
      this.resizeEdges.bottom
    ) {
      const distanceFromBottom =
        rect.bottom - event.clientY;

      if (distanceFromBottom <= this.resizeZone) {
        detectType = 'bottom';
      }
    }

    if (detectType) {
      event.preventDefault();
      event.stopPropagation();

      this.isResizing = true;
      this.resizeType = detectType;
      this.startX = event.clientX;
      this.startY = event.clientY;
      this.startWidth = rect.width;
      this.startHeight = rect.height;

      elem.style.userSelect = 'none';

      if (
        detectType === 'left' ||
        detectType === 'right'
      ) {
        elem.style.cursor = 'col-resize';
      } else {
        elem.style.cursor = 'row-resize';
      }
    }
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent): void {
    if (
      !this.isResizing ||
      event.buttons === 0
    ) {
      return;
    }

    event.preventDefault();

    const elem = this.el.nativeElement as HTMLElement;

    this.ngZone.run(() => {

      // RIGHT
      if (this.resizeType === 'right') {
        const newWidth =
          this.startWidth +
          (event.clientX - this.startX);

        const width =
          this.constrainWidth(newWidth);

        this.resizeEnd.emit({
          edges: {
            right: width
          }
        });
      }

      // LEFT
      else if (this.resizeType === 'left') {
        const mouseDifference =
          event.clientX - this.startX;

        const newWidth =
          this.startWidth - mouseDifference;

        const width =
          this.constrainWidth(newWidth);

        const newLeft =
          event.clientX - this.startX;

        elem.style.left =
          `${newLeft}px`;

        this.resizeEnd.emit({
          edges: {
            left: width
          }
        });
      }

      // BOTTOM
      else if (this.resizeType === 'bottom') {
        const newHeight =
          this.startHeight +
          (event.clientY - this.startY);

        const height =
          this.constrainHeight(newHeight);

        this.resizeEnd.emit({
          edges: {
            bottom: height
          }
        });
      }

      // TOP
      else if (this.resizeType === 'top') {
        const mouseDifference =
          event.clientY - this.startY;

        const newHeight =
          this.startHeight - mouseDifference;

        const height =
          this.constrainHeight(newHeight);

        this.resizeEnd.emit({
          edges: {
            top: height
          }
        });
      }
    });
  }

  @HostListener('document:mouseup')
  onMouseUp(): void {
    if (!this.isResizing) {
      return;
    }

    const elem =
      this.el.nativeElement as HTMLElement;

    this.isResizing = false;
    elem.style.userSelect = 'auto';
    this.resizeType = null;
    elem.style.cursor = 'default';
  }

  private constrainWidth(width: number): number {
    if (
      this.minWidth !== undefined &&
      width < this.minWidth
    ) {
      return this.minWidth;
    }

    if (
      this.maxWidth !== undefined &&
      width > this.maxWidth
    ) {
      return this.maxWidth;
    }

    return width;
  }

  private constrainHeight(height: number): number {
    if (
      this.minHeight !== undefined &&
      height < this.minHeight
    ) {
      return this.minHeight;
    }

    if (
      this.maxHeight !== undefined &&
      height > this.maxHeight
    ) {
      return this.maxHeight;
    }

    return height;
  }
}
