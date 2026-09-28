import {
  Component,
  OnInit,
  signal
} from '@angular/core';

import {
  ActivatedRoute,
  Router
} from '@angular/router';

import {
  CommonModule
} from '@angular/common';

import {
  MarketDataService,
  HistoricalPrice
} from '../../core/services/market-data';

import {
  finalize
} from 'rxjs';

import {
  BaseChartDirective
} from 'ng2-charts';

import {
  ChartOptions,
  Chart,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  LineController,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';

Chart.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  LineController,
  Title,
  Tooltip,
  Legend,
  Filler
);


@Component({
  selector: 'app-history',
  standalone: true,
  imports: [
    CommonModule,
    BaseChartDirective
  ],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss'
})
export class HistoryComponent implements OnInit {

  symbol =
    signal<string>('');

  historicalPrices =
    signal<HistoricalPrice[]>([]);

  historicalLoading =
    signal(true);

  historicalErrorMessage =
    signal('');

  chartData =
    signal<any>({ labels: [], datasets: [] });

  chartOptions: ChartOptions = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        backgroundColor: 'rgba(13, 27, 42, 0.9)',
        titleColor: '#00d4aa',
        bodyColor: '#c5d3e0',
        borderColor: 'rgba(0, 212, 170, 0.3)',
        borderWidth: 1,
        padding: 10,
        displayColors: false
      }
    },
    scales: {
      x: {
        grid: {
          color: 'rgba(0, 212, 170, 0.1)'
        },
        ticks: {
          color: '#8a9aaa',
          font: {
            size: 12
          }
        }
      },
      y: {
        grid: {
          color: 'rgba(0, 212, 170, 0.1)'
        },
        ticks: {
          color: '#8a9aaa',
          font: {
            size: 12
          }
        }
      }
    }
  };


  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private marketDataService: MarketDataService
  ) {}


  ngOnInit(): void {

    this.route.params.subscribe(params => {

      const sym = params['symbol'];

      if (sym) {

        this.symbol.set(sym);

        this.loadHistoricalData(sym);

      }

    });

  }


  loadHistoricalData(symbol: string): void {

    this.historicalPrices.set([]);

    this.historicalErrorMessage.set('');

    this.historicalLoading.set(true);

    this.marketDataService
      .getHistoricalData(symbol)
      .pipe(

        finalize(() => {

          this.historicalLoading.set(false);

        })

      )
      .subscribe({

        next: prices => {

          // Sort by date descending (latest first)
          const sortedPrices = prices.sort((a, b) => 
            new Date(b.date).getTime() - new Date(a.date).getTime()
          );

          this.historicalPrices.set(sortedPrices);

          if (sortedPrices.length === 0) {

            this.historicalErrorMessage.set(
              'No historical data available for this symbol.'
            );

          } else {

            this.generateChartData(sortedPrices);

          }

        },

        error: () => {

          this.historicalPrices.set([]);

          this.historicalErrorMessage.set(
            'Unable to load historical data.'
          );

        }

      });

  }


  goBack(): void {

    this.router.navigate(['/dashboard']);

  }


  generateChartData(prices: HistoricalPrice[]): void {

    // Sort chronologically (oldest first) for chart display
    const chronoSorted = [...prices].sort((a, b) => 
      new Date(a.date).getTime() - new Date(b.date).getTime()
    );

    const labels = chronoSorted.map(p => {
      const date = new Date(p.date);
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    });

    const closeData = chronoSorted.map(p => p.close);

    this.chartData.set({
      labels: labels,
      datasets: [
        {
          label: 'Close Price',
          data: closeData,
          borderColor: '#00d4aa',
          backgroundColor: 'rgba(0, 212, 170, 0.1)',
          borderWidth: 2,
          fill: true,
          pointRadius: 0,
          pointHoverRadius: 6,
          pointBackgroundColor: '#00d4aa',
          pointBorderColor: '#00d4aa',
          pointBorderWidth: 2,
          tension: 0.4
        }
      ]
    });

  }

}
