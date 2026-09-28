export const environment = {
  production: false,

  /*
   * Existing trading-platform backend.
   *
   * Account, holdings, orders, and database-backed
   * market instruments continue using this API.
   */
  apiUrl: 'http://localhost:8080/api',

  /*
   * Separate market-data-service backend.
   *
   * Historical CSV data is served from port 8081.
   */
  marketDataApiUrl: 'http://localhost:8081/api'
};