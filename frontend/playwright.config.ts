import { defineConfig } from '@playwright/test'

const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'

export default defineConfig({
  testDir: './tests',
  workers: 1,
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    [
      'allure-playwright',
      {
        resultsDir: 'allure-results',
        environmentInfo: {
          Anwendung: 'Terminbuchung (Spring Boot + React/Vite)',
          Backend: 'http://127.0.0.1:8080',
          Frontend: 'http://127.0.0.1:5173',
          Browser: 'Chromium',
        },
      },
    ],
  ],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    locale: 'de-AT',
    timezoneId: 'Europe/Vienna',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: `${mvnw} spring-boot:run`,
      cwd: '../backend',
      url: 'http://127.0.0.1:8080/api/slots',
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
  ],
})
