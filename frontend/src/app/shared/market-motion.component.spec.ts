import { TestBed } from '@angular/core/testing';
import { MarketMotionComponent } from './market-motion.component';

it('keeps SVG references and orbit paths valid when multiple artworks share a page', async () => {
  await TestBed.configureTestingModule({ imports: [MarketMotionComponent] }).compileComponents();
  const first = TestBed.createComponent(MarketMotionComponent);
  const second = TestBed.createComponent(MarketMotionComponent);
  second.componentRef.setInput('compact', true);
  first.detectChanges();
  second.detectChanges();
  const seenIds = new Set<string>();
  for (const fixture of [first, second]) {
    const host = fixture.nativeElement as HTMLElement;
    expect(host.getAttribute('aria-hidden')).toBe('true');
    expect(host.querySelector('svg')?.getAttribute('focusable')).toBe('false');
    const ids = new Set([...host.querySelectorAll('[id]')].map((node) => node.id));
    for (const id of ids) {
      expect(seenIds.has(id)).toBe(false);
      seenIds.add(id);
    }
    for (const node of host.querySelectorAll('[fill], [stroke], [clip-path]')) {
      for (const attribute of ['fill', 'stroke', 'clip-path']) {
        const reference = node.getAttribute(attribute)?.match(/^url\(#(.+)\)$/)?.[1];
        if (reference) expect(ids.has(reference)).toBe(true);
      }
    }
    const track = host.querySelector('.orbit-comet')!;
    expect(fixture.componentInstance.orbitMotionPath).toBe(`path("${track.getAttribute('d')}")`);
    const satellites = host.querySelectorAll<SVGGElement>('.orbit-satellite');
    expect(satellites).toHaveLength(2);
    for (const satellite of satellites) {
      expect(satellite.style.getPropertyValue('offset-path')).toBe(
        fixture.componentInstance.orbitMotionPath,
      );
    }
    expect(host.querySelectorAll('script, animate, animateMotion, animateTransform')).toHaveLength(
      0,
    );
  }
  expect(second.nativeElement.classList.contains('compact')).toBe(true);
});
